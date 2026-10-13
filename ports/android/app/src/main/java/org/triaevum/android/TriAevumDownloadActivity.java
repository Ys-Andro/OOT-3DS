package org.triaevum.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.content.SharedPreferences;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Seleciona e processa a pasta da ROM do jogo, copia os arquivos necessários
 * para o diretório de dados do app e inicia o jogo.
 */
public class TriAevumDownloadActivity extends Activity {

    private static final String TAG = "TriAevumDownloader";
    private static final int REQUEST_CODE_PICK_ROM_DIR = 1001;
    private static final int REQUEST_CODE_PICK_SAVE_DIR = 1002;
    private static final String PREFS_NAME = "org.triaevum.android_preferences";
    private static final String PREF_ROM_DIR_URI = "selected_rom_directory_uri";

    private TextView mTvStatus;
    private TextView mTvPercent;
    private TextView mTvDetails;
    private TextView mTvTouchToStart;
    private ProgressBar mPbDownload;
    private Button mBtnAction;
    private Button mBtnChangeDirectory;
    private Button mBtnImportSave;
    private View mLayoutProgressDetails;
    private View mRootLayout;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean mIsDownloading = false;
    private volatile boolean mReadyToStart = false;

    public static boolean isGameInstalled(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) return false;
        File romfs = new File(root, "romfs.bin");
        File code = new File(root, "code.bin");
        File manifest = new File(root, "process-manifest.json");
        return romfs.isFile() && romfs.length() > 100_000_000L
                && code.isFile() && code.length() > 0
                && manifest.isFile() && manifest.length() > 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        hideSystemBars();

        setContentView(R.layout.activity_downloader);

        mRootLayout            = findViewById(R.id.layout_downloader_root);
        mTvStatus              = findViewById(R.id.tv_download_status);
        mTvPercent             = findViewById(R.id.tv_download_percent);
        mTvDetails             = findViewById(R.id.tv_download_details);
        mTvTouchToStart        = findViewById(R.id.tv_touch_to_start);
        mPbDownload            = findViewById(R.id.pb_download);
        mBtnAction             = findViewById(R.id.btn_download_action);
        mBtnChangeDirectory    = findViewById(R.id.btn_change_directory);
        mBtnImportSave         = findViewById(R.id.btn_import_citra_save);
        mLayoutProgressDetails = findViewById(R.id.layout_progress_details);

        mRootLayout.setOnClickListener(v -> {
            if (mReadyToStart) {
                launchGame();
            }
        });

        mLayoutProgressDetails.setVisibility(View.GONE);
        mBtnAction.setVisibility(View.VISIBLE);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedDirUri = prefs.getString(PREF_ROM_DIR_URI, null);

        if (isGameInstalled(this)) {
            mReadyToStart = true;
            mBtnAction.setText("Iniciar juego");
            mBtnAction.setOnClickListener(v -> launchGame());
            mBtnChangeDirectory.setVisibility(View.VISIBLE);
            mBtnChangeDirectory.setOnClickListener(v -> pickRomDirectory());
            mBtnImportSave.setVisibility(View.VISIBLE);
            mBtnImportSave.setOnClickListener(v -> pickCitraSaveDirectory());
            mTvStatus.setText("La ROM está lista. Toca en «Iniciar juego» o «Cambiar carpeta».");
            mTvTouchToStart.setVisibility(View.VISIBLE);
            AlphaAnimation pulse = new AlphaAnimation(0.25f, 1.0f);
            pulse.setDuration(600);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            mTvTouchToStart.startAnimation(pulse);
        } else {
            mBtnAction.setText("Seleccionar carpeta de la ROM");
            mTvStatus.setText("Selecciona la carpeta que contiene los archivos de la ROM para comenzar");
            mBtnAction.setOnClickListener(v -> requestStoragePermissionAndPickRomDirectory());
            mBtnChangeDirectory.setVisibility(View.GONE);
            mBtnImportSave.setVisibility(View.GONE);
            mTvTouchToStart.setVisibility(View.GONE);
        }
    }

    private void setupReadyToStart() {
        File targetDir = getExternalFilesDir(null);
        if (targetDir != null) {
            unpackBundledAssets(targetDir);
            new File(targetDir, "resources").mkdirs();
            new File(targetDir, "savedata").mkdirs();
        }
        mReadyToStart = true;
        mIsDownloading = false;
        mTvStatus.setText("¡La ROM está lista para jugar!");
        mPbDownload.setProgress(100);
        mTvPercent.setText("100%");
        mLayoutProgressDetails.setVisibility(View.GONE);
        mBtnAction.setVisibility(View.VISIBLE);
        mBtnAction.setText("Iniciar juego");
        mBtnAction.setOnClickListener(v -> launchGame());
        if (mBtnChangeDirectory != null) {
            mBtnChangeDirectory.setVisibility(View.VISIBLE);
            mBtnChangeDirectory.setOnClickListener(v -> pickRomDirectory());
        }
        if (mBtnImportSave != null) {
            mBtnImportSave.setVisibility(View.VISIBLE);
            mBtnImportSave.setOnClickListener(v -> pickCitraSaveDirectory());
        }
        mTvTouchToStart.setVisibility(View.VISIBLE);
        AlphaAnimation pulse = new AlphaAnimation(0.25f, 1.0f);
        pulse.setDuration(600);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        mTvTouchToStart.startAnimation(pulse);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
    }

    private void hideSystemBars() {
        try {
            WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } catch (Exception ignored) {}
    }

    private synchronized void launchGame() {
        // Re-verificar se o jogo está instalado antes de iniciar
        if (!isGameInstalled(this)) {
            mReadyToStart = false;
            mMainHandler.post(() -> {
                mTvStatus.setText("No se encontraron los archivos del juego. Selecciona de nuevo la carpeta de la ROM.");
                mBtnAction.setVisibility(View.VISIBLE);
                mBtnAction.setText("Seleccionar carpeta de la ROM");
                mBtnAction.setOnClickListener(v -> requestStoragePermissionAndPickRomDirectory());
                if (mBtnChangeDirectory != null) mBtnChangeDirectory.setVisibility(View.GONE);
                mTvTouchToStart.setVisibility(View.GONE);
                mTvTouchToStart.clearAnimation();
            });
            return;
        }
        Intent intent = new Intent(this, TriAevumActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void requestStoragePermissionAndPickRomDirectory() {
        // ACTION_OPEN_DOCUMENT_TREE grants access to the user-selected tree;
        // broad storage permissions are neither required nor requested.
        pickRomDirectory();
    }

    private void pickRomDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_ROM_DIR);
    }

    private void pickCitraSaveDirectory() {
        if (!mReadyToStart || mIsDownloading) {
            android.widget.Toast.makeText(this, "Espera a que termine la preparación del juego.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_SAVE_DIR);
    }

    private void importCitraSave(Uri treeUri) {
        if (mIsDownloading) return;
        mIsDownloading = true;
        mMainHandler.post(() -> {
            mTvStatus.setText("Importando partida de Citra…");
            mTvDetails.setText("Copiando los archivos de la partida…");
            mPbDownload.setIndeterminate(true);
            mLayoutProgressDetails.setVisibility(View.VISIBLE);
            mBtnAction.setEnabled(false);
            mBtnChangeDirectory.setEnabled(false);
            mBtnImportSave.setEnabled(false);
        });

        mExecutor.execute(() -> {
            File root = getExternalFilesDir(null);
            if (root == null) {
                finishSaveImportUi("No se pudo acceder al almacenamiento de la aplicación.", false);
                return;
            }
            File saveDir = new File(root, "savedata");
            File stagingDir = new File(root, "citra_import_staging_" + System.currentTimeMillis());
            try {
                DocumentFile picked = DocumentFile.fromTreeUri(this, treeUri);
                if (picked == null || !picked.exists() || !picked.isDirectory()) {
                    throw new IOException("La carpeta seleccionada no es accesible.");
                }
                if (!stagingDir.mkdirs()) throw new IOException("No se pudo preparar la importación.");
                int saveCount = 0;
                boolean hasSystem = false;
                DocumentFile[] entries = picked.listFiles();
                if (entries == null) throw new IOException("No se pudo leer el contenido de la carpeta.");

                for (DocumentFile entry : entries) {
                    if (!entry.isFile() || entry.getName() == null) continue;
                    String name = entry.getName();
                    boolean saveFile = name.matches("(?i)save[0-9]+\\.bin");
                    boolean systemFile = "system.dat".equalsIgnoreCase(name);
                    if (!saveFile && !systemFile) continue;
                    long size = entry.length();
                    if (size <= 0 || size > 1024 * 1024) {
                        throw new IOException("Tamaño no válido para " + name + " (" + size + " bytes).");
                    }
                    String normalizedName = systemFile ? "system.dat" : name.toLowerCase(Locale.ROOT);
                    File staged = new File(stagingDir, normalizedName);
                    try (InputStream in = getContentResolver().openInputStream(entry.getUri());
                         OutputStream out = new FileOutputStream(staged)) {
                        if (in == null) throw new IOException("No se pudo leer " + name);
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    }
                    if (staged.length() != size) {
                        throw new IOException("La copia de " + name + " quedó incompleta.");
                    }
                    if (saveFile) saveCount++;
                    if (systemFile) hasSystem = true;
                }

                if (saveCount == 0) {
                    throw new IOException("No encontré save00.bin (u otro saveNN.bin). Elige la carpeta donde se ven directamente esos archivos.");
                }
                if (!saveDir.exists() && !saveDir.mkdirs()) {
                    throw new IOException("No se pudo crear la carpeta de partidas.");
                }

                File[] stagedFiles = stagingDir.listFiles();
                if (stagedFiles == null || stagedFiles.length == 0) {
                    throw new IOException("No hay archivos de partida válidos para importar.");
                }
                boolean needsBackup = false;
                for (File staged : stagedFiles) {
                    if (new File(saveDir, staged.getName()).exists()) needsBackup = true;
                }
                File backupDir = new File(saveDir, "backup_citra_import_" + System.currentTimeMillis());
                if (needsBackup && !backupDir.mkdirs()) {
                    throw new IOException("No se pudo crear la copia de seguridad de la partida actual.");
                }

                // Respaldar todo lo que vaya a ser reemplazado antes de modificar la partida.
                for (File staged : stagedFiles) {
                    File destination = new File(saveDir, staged.getName());
                    if (destination.exists()) copyLocalFile(destination, new File(backupDir, destination.getName()));
                }
                // Instalar únicamente saveNN.bin y system.dat; los demás datos quedan intactos.
                for (File staged : stagedFiles) {
                    File destination = new File(saveDir, staged.getName());
                    File temp = new File(saveDir, staged.getName() + ".importing");
                    copyLocalFile(staged, temp);
                    if (destination.exists() && !destination.delete()) {
                        throw new IOException("No se pudo reemplazar " + destination.getName());
                    }
                    if (!temp.renameTo(destination)) {
                        throw new IOException("No se pudo instalar " + destination.getName());
                    }
                }

                final int importedSaves = saveCount;
                final boolean importedSystem = hasSystem;
                final String backupMessage = needsBackup ? " Se guardó una copia de seguridad." : "";
                finishSaveImportUi("Partida importada: " + importedSaves + " archivo(s) save*.bin"
                    + (importedSystem ? " y system.dat." : ". system.dat no estaba en la carpeta.")
                    + backupMessage + " Inicia el juego para comprobar la compatibilidad.", true);
            } catch (Exception e) {
                Log.e(TAG, "Error al importar la partida de Citra", e);
                finishSaveImportUi("No se importó la partida: " + e.getMessage(), false);
            } finally {
                deleteRecursively(stagingDir);
            }
        });
    }

    private void finishSaveImportUi(String message, boolean success) {
        mIsDownloading = false;
        mMainHandler.post(() -> {
            mTvStatus.setText(message);
            mTvDetails.setText(success
                ? "Reinicia el juego si ya estaba abierto."
                : "Elige la carpeta que contiene directamente save00.bin.");
            mPbDownload.setIndeterminate(false);
            mPbDownload.setProgress(success ? 100 : 0);
            mTvPercent.setText(success ? "100%" : "");
            mLayoutProgressDetails.setVisibility(View.VISIBLE);
            mBtnAction.setEnabled(true);
            mBtnChangeDirectory.setEnabled(true);
            mBtnImportSave.setEnabled(true);
        });
    }

    private static void copyLocalFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent.getName());
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.flush();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_SAVE_DIR && resultCode == RESULT_OK && data != null) {
            Uri saveUri = data.getData();
            if (saveUri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(saveUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo conservar el permiso de lectura de la partida", e);
                }
                importCitraSave(saveUri);
            }
            return;
        }
        if (requestCode == REQUEST_CODE_PICK_ROM_DIR && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo conservar el permiso de acceso a la carpeta", e);
                }

                // Salvar o diretório selecionado nas SharedPreferences
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_ROM_DIR_URI, uri.toString())
                    .apply();

                scanAndProcessRomDirectory(uri);
            }
        }
    }

    private static class RomDiscoveryResult {
        DocumentFile codeBin;
        DocumentFile romfsBin;
        DocumentFile exheaderBin;
        DocumentFile romContainerFile;

        boolean hasExtractedFiles() {
            return codeBin != null && romfsBin != null;
        }

        boolean hasAnyGameData() {
            return hasExtractedFiles() || romContainerFile != null;
        }
    }

    private RomDiscoveryResult discoverRomFiles(DocumentFile dir) {
        RomDiscoveryResult res = new RomDiscoveryResult();
        DocumentFile[] files = dir.listFiles();
        if (files == null) return res;

        for (DocumentFile f : files) {
            if (f.isFile()) {
                inspectFileForRom(f, res);
            }
        }

        // Se não achou no diretório raiz, verificar subpastas imediatas (1 nível)
        if (!res.hasAnyGameData()) {
            for (DocumentFile f : files) {
                if (f.isDirectory()) {
                    DocumentFile[] sub = f.listFiles();
                    if (sub != null) {
                        for (DocumentFile sf : sub) {
                            if (sf.isFile()) {
                                inspectFileForRom(sf, res);
                            }
                        }
                    }
                    if (res.hasAnyGameData()) {
                        break;
                    }
                }
            }
        }

        return res;
    }

    private void inspectFileForRom(DocumentFile f, RomDiscoveryResult res) {
        String name = f.getName();
        if (name == null) return;
        String lower = name.toLowerCase(Locale.ROOT);

        if ("code.bin".equals(lower)) {
            res.codeBin = f;
        } else if ("romfs.bin".equals(lower)) {
            res.romfsBin = f;
        } else if ("exheader.bin".equals(lower)) {
            res.exheaderBin = f;
        } else if (lower.endsWith(".3ds") || lower.endsWith(".cci")) {
            if (res.romContainerFile == null) {
                res.romContainerFile = f;
            }
        }
    }

    private void scanAndProcessRomDirectory(Uri treeUri) {
        if (mIsDownloading) return;
        mIsDownloading = true;

        mMainHandler.post(() -> {
            mBtnAction.setVisibility(View.GONE);
            mTvTouchToStart.setVisibility(View.GONE);
            mLayoutProgressDetails.setVisibility(View.VISIBLE);
            mTvStatus.setText("Buscando archivos de la ROM en la carpeta...");
            mPbDownload.setIndeterminate(true);
            mTvPercent.setText("");
            mTvDetails.setText("Examinando la carpeta seleccionada...");
        });

        mExecutor.execute(() -> {
            File targetDir = getExternalFilesDir(null);
            if (targetDir == null) {
                showError("El almacenamiento externo no está disponible");
                return;
            }
            if (!targetDir.exists()) targetDir.mkdirs();

            try {
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
                if (pickedDir == null || !pickedDir.exists() || !pickedDir.isDirectory()) {
                    showError("La carpeta seleccionada no existe o no es accesible.");
                    return;
                }

                RomDiscoveryResult result = discoverRomFiles(pickedDir);

                if (!result.hasAnyGameData()) {
                    showError("No se encontraron archivos de ROM (.3ds, .cci o code.bin/romfs.bin) en la carpeta seleccionada.");
                    return;
                }

                if (result.hasExtractedFiles()) {
                    processExtractedRomFiles(result, targetDir);
                } else {
                    processRomContainerFile(result.romContainerFile, targetDir);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error al procesar la carpeta de la ROM", e);
                showError("Error al procesar la ROM: " + e.getMessage());
            }
        });
    }

    private void processExtractedRomFiles(RomDiscoveryResult result, File targetDir) throws Exception {
        mMainHandler.post(() -> {
            mTvStatus.setText("¡Archivos de la ROM encontrados! Preparando el juego...");
            mPbDownload.setIndeterminate(false);
            mPbDownload.setProgress(0);
            mTvPercent.setText("0%");
            mTvDetails.setText("Copiando archivos necesarios...");
        });

        long totalBytes = result.codeBin.length() + result.romfsBin.length();
        if (result.exheaderBin != null) {
            totalBytes += result.exheaderBin.length();
        }
        if (totalBytes <= 0) totalBytes = 480_000_000L;

        long[] copiedBytes = new long[]{0};

        // 1. Copiar code.bin
        File destCode = new File(targetDir, "code.bin");
        copyDocumentToFile(result.codeBin, destCode, "Copiando code.bin", totalBytes, copiedBytes);

        // Adaptar USA code se necessário
        try {
            byte[] codeBytes = Files.readAllBytes(destCode.toPath());
            String codeSha = CtrRomExtractor.sha256Hex(codeBytes);
            if ("ef210566e1d9d16879a746dfb063fcbad232f0171d860de906531ecc526cc020".equalsIgnoreCase(codeSha)) {
                mMainHandler.post(() -> mTvDetails.setText("Adaptando el ejecutable regional..."));
                codeBytes = CtrRomExtractor.adaptUsaCodeToEur(this, codeBytes);
                try (FileOutputStream fos = new FileOutputStream(destCode)) {
                    fos.write(codeBytes);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Aviso al verificar el hash de code.bin", e);
        }

        // 2. Copiar exheader.bin se presente
        if (result.exheaderBin != null) {
            File destExheader = new File(targetDir, "exheader.bin");
            copyDocumentToFile(result.exheaderBin, destExheader, "Copiando exheader.bin", totalBytes, copiedBytes);
        }

        // 3. Copiar romfs.bin
        File destRomfs = new File(targetDir, "romfs.bin");
        copyDocumentToFile(result.romfsBin, destRomfs, "Copiando romfs.bin", totalBytes, copiedBytes);

        // Normalizar romfs se necessário
        try {
            CtrRomExtractor.normalizeRomFsIfNeeded(destRomfs);
        } catch (Exception e) {
            Log.w(TAG, "Aviso al normalizar RomFS", e);
        }

        // 4. Descompactar bundled assets (launch config, overrides, manifest)
        unpackBundledAssets(targetDir);

        // 5. Criar diretórios obrigatórios
        new File(targetDir, "resources").mkdirs();
        new File(targetDir, "savedata").mkdirs();

        mMainHandler.post(this::setupReadyToStart);
    }

    private void processRomContainerFile(DocumentFile romContainerFile, File targetDir) throws Exception {
        mMainHandler.post(() -> {
            mTvStatus.setText("ROM encontrada: " + romContainerFile.getName());
            mPbDownload.setIndeterminate(false);
            mPbDownload.setProgress(0);
            mTvPercent.setText("0%");
            mTvDetails.setText("Copiando el archivo de ROM para procesarlo...");
        });

        long romLength = romContainerFile.length();
        long[] copied = new long[]{0};
        File tempRomFile = new File(targetDir, "temp_rom.3ds");
        copyDocumentToFile(romContainerFile, tempRomFile, "Copiando ROM", romLength, copied);

        CtrRomExtractor.extractRom(this, tempRomFile, targetDir, (stage, percent) -> {
            mMainHandler.post(() -> {
                mTvStatus.setText(stage);
                mPbDownload.setProgress(percent);
                mTvPercent.setText(percent + "%");
                mTvDetails.setText("Extrayendo y convirtiendo la ROM...");
            });
        });

        tempRomFile.delete();

        unpackBundledAssets(targetDir);

        new File(targetDir, "resources").mkdirs();
        new File(targetDir, "savedata").mkdirs();

        mMainHandler.post(this::setupReadyToStart);
    }

    private void copyDocumentToFile(DocumentFile source, File destination, String label, long totalExpectedBytes, long[] bytesOffsetSoFar) throws IOException {
        Uri uri = source.getUri();
        long lastUiUpdate = 0;
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(destination)) {
            if (in == null) throw new IOException("Não foi possível ler " + source.getName());
            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                bytesOffsetSoFar[0] += read;
                long now = SystemClock.elapsedRealtime();
                if (now - lastUiUpdate > 100) {
                    lastUiUpdate = now;
                    final long current = bytesOffsetSoFar[0];
                    final int pct = totalExpectedBytes > 0 ? (int) Math.min(100, (current * 100) / totalExpectedBytes) : 0;
                    mMainHandler.post(() -> {
                        mPbDownload.setProgress(pct);
                        mTvPercent.setText(pct + "%");
                        double currentMB = current / (1024.0 * 1024.0);
                        double totalMB = totalExpectedBytes / (1024.0 * 1024.0);
                        mTvDetails.setText(String.format(Locale.US, "%s (%.1f MB / %.1f MB)", label, currentMB, totalMB));
                    });
                }
            }
            out.flush();
        }
    }

    private void showError(String msg) {
        mIsDownloading = false;
        mMainHandler.post(() -> {
            mTvStatus.setText(msg);
            mPbDownload.setIndeterminate(false);
            mPbDownload.setProgress(0);
            mTvPercent.setText("");
            mTvDetails.setText("Pulsa el botón de abajo para seleccionar la carpeta con los archivos de la ROM.");
            mBtnAction.setVisibility(View.VISIBLE);
            mBtnAction.setText("Seleccionar carpeta de la ROM");
            mBtnAction.setOnClickListener(v -> requestStoragePermissionAndPickRomDirectory());
            if (mBtnChangeDirectory != null) mBtnChangeDirectory.setVisibility(View.GONE);
        });
    }

    private static boolean isZipFile(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = is.read(header);
            return read == 4 && header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
        } catch (Exception e) {
            return false;
        }
    }

    private static void extractZip(File zipFile, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[65536];
            while ((entry = zis.getNextEntry()) != null) {
                File target = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(target)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void unpackBundledAssets(File targetDir) {
        try {
            String[] files = getAssets().list("game");
            if (files != null) {
                for (String filename : files) {
                    File dest = new File(targetDir, filename);
                    // Sempre sobrescrever arquivos críticos de configuração
                    // para garantir que versões antigas sejam atualizadas
                    boolean alwaysOverwrite = filename.equals("process-manifest.json")
                            || filename.equals("TriAevum.android.launch.json")
                            || filename.equals("topscreen_ui.json");
                    if (!dest.exists() || alwaysOverwrite) {
                        try (InputStream in = getAssets().open("game/" + filename);
                             OutputStream out = new FileOutputStream(dest)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not unpack game assets", e);
        }
    }
}

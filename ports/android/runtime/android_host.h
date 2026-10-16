#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>

enum class AndroidQuickStateStatus : int32_t {
  Failed = -1,
  Idle = 0,
  Saving = 1,
  Saved = 2,
  Loading = 3,
  Loaded = 4,
};

struct AndroidOverlayInputState {
  std::atomic<uint32_t> buttons{0};
  std::atomic<float> circlePadX{0.0f};
  std::atomic<float> circlePadY{0.0f};
  std::atomic<float> cStickX{0.0f};
  std::atomic<float> cStickY{0.0f};
  std::atomic<float> touchX{0.0f};
  std::atomic<float> touchY{0.0f};
  std::atomic<bool> touchPressed{false};
  std::atomic<bool> touchEnabled{false};
  std::atomic<bool> swapScreens{false};
  std::atomic<bool> quickSaveRequested{false};
  std::atomic<bool> quickLoadRequested{false};
  std::atomic<AndroidQuickStateStatus> quickStateStatus{AndroidQuickStateStatus::Idle};
};

namespace Oot3dNativeGame {
class TopScreenUiConfigRuntime;
}

AndroidOverlayInputState &GetAndroidOverlayInputState();

void InitializeAndroidGameHost();
void ShutdownAndroidGameHost();
void SetAndroidStoragePath(const std::string &path);
std::string GetAndroidStoragePath();
int RunOot3dNativeGameMain(int argc, char** argv);

void RegisterAndroidTopScreenConfigRuntime(
    std::shared_ptr<Oot3dNativeGame::TopScreenUiConfigRuntime> runtime);
void NotifyAndroidTopScreenConfigChanged();

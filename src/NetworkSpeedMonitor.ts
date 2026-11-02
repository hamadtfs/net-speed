import {
  NativeEventEmitter,
  NativeModules,
  Platform,
  PermissionsAndroid,
} from 'react-native';

export type SpeedSample = {
  download: number;
  upload: number;
  total: number;
  onWifi?: boolean;
  signal?: number | null;
};

export type OverlaySettings = {
  enabled: boolean;
  permitted: boolean;
  offset: number;
  darkText: boolean;
};

export type UsageDay = {
  dateKey: string;
  label: string;
  isToday?: boolean;
  mobile: number;
  wifi: number;
  total: number;
};

export type DailyUsage = {
  days: UsageDay[];
  month: {
    label: string;
    mobile: number;
    wifi: number;
    total: number;
  };
};

type NativeMonitor = {
  start: () => Promise<boolean>;
  stop: () => Promise<boolean>;
  isMonitoring: () => Promise<boolean>;
  isIgnoringBatteryOptimizations: () => Promise<boolean>;
  openBatteryOptimizationSettings: () => Promise<boolean>;
  getOverlaySettings: () => Promise<OverlaySettings>;
  setOverlayEnabled: (enabled: boolean) => Promise<boolean>;
  setOverlayOffset: (offsetDp: number) => Promise<number>;
  openOverlayPermissionSettings: () => Promise<boolean>;
  setOverlayDarkText: (dark: boolean) => Promise<boolean>;
  getDailyUsage: () => Promise<DailyUsage>;
  clearDailyUsage: () => Promise<boolean>;
  isAutoStartEnabled: () => Promise<boolean>;
  setAutoStartEnabled: (enabled: boolean) => Promise<boolean>;
  addListener: (eventName: string) => void;
  removeListeners: (count: number) => void;
};

const LINKING_ERROR =
  'NetworkSpeedMonitor native module is not linked. Rebuild the Android app.';

const Native: NativeMonitor =
  Platform.OS === 'android' && NativeModules.NetworkSpeedMonitor
    ? NativeModules.NetworkSpeedMonitor
    : new Proxy({} as NativeMonitor, {
        get() {
          throw new Error(LINKING_ERROR);
        },
      });

const emitter =
  Platform.OS === 'android'
    ? new NativeEventEmitter(NativeModules.NetworkSpeedMonitor)
    : null;

export async function requestNotificationPermission(): Promise<boolean> {
  if (Platform.OS !== 'android' || Platform.Version < 33) {
    return true;
  }
  const result = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
  );
  return result === PermissionsAndroid.RESULTS.GRANTED;
}

export function startMonitoring(): Promise<boolean> {
  return Native.start();
}

export function stopMonitoring(): Promise<boolean> {
  return Native.stop();
}

export function isMonitoring(): Promise<boolean> {
  return Native.isMonitoring();
}

export function isIgnoringBatteryOptimizations(): Promise<boolean> {
  return Native.isIgnoringBatteryOptimizations();
}

export function openBatteryOptimizationSettings(): Promise<boolean> {
  return Native.openBatteryOptimizationSettings();
}

export function getOverlaySettings(): Promise<OverlaySettings> {
  return Native.getOverlaySettings();
}

export function setOverlayEnabled(enabled: boolean): Promise<boolean> {
  return Native.setOverlayEnabled(enabled);
}

export function setOverlayOffset(offsetDp: number): Promise<number> {
  return Native.setOverlayOffset(offsetDp);
}

export function setOverlayDarkText(dark: boolean): Promise<boolean> {
  return Native.setOverlayDarkText(dark);
}

export function openOverlayPermissionSettings(): Promise<boolean> {
  return Native.openOverlayPermissionSettings();
}

export function getDailyUsage(): Promise<DailyUsage> {
  return Native.getDailyUsage();
}

export function clearDailyUsage(): Promise<boolean> {
  return Native.clearDailyUsage();
}

export function isAutoStartEnabled(): Promise<boolean> {
  return Native.isAutoStartEnabled();
}

export function setAutoStartEnabled(enabled: boolean): Promise<boolean> {
  return Native.setAutoStartEnabled(enabled);
}

export function formatData(bytes: number): string {
  const value = Math.abs(bytes);
  if (value < 1024) {
    return `${value.toFixed(0)} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function subscribeSpeed(
  listener: (sample: SpeedSample) => void,
): () => void {
  if (!emitter) {
    return () => {};
  }
  const sub = emitter.addListener('NetworkSpeedUpdate', listener);
  return () => sub.remove();
}

export function formatSpeed(bytesPerSecond: number): string {
  const speed = Math.abs(bytesPerSecond);
  // Never show B/s — always start at KB/s with capital letters.
  if (speed < 1024 * 1024) {
    return `${Math.round(speed / 1024)} KB/s`;
  }
  if (speed < 1024 * 1024 * 1024) {
    const mb = speed / (1024 * 1024);
    if (mb < 10) {
      return `${mb.toFixed(2)} MB/s`;
    }
    if (mb < 100) {
      return `${mb.toFixed(1)} MB/s`;
    }
    return `${mb.toFixed(0)} MB/s`;
  }
  return `${(speed / (1024 * 1024 * 1024)).toFixed(2)} GB/s`;
}

export function formatDuration(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  if (h > 0) {
    return `${h}h ${m.toString().padStart(2, '0')}m`;
  }
  if (m > 0) {
    return `${m}m ${r.toString().padStart(2, '0')}s`;
  }
  return `${r}s`;
}

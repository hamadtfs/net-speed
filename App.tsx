import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  ActivityIndicator,
  Alert,
  AppState,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  clearDailyUsage,
  formatData,
  formatDuration,
  formatSpeed,
  getDailyUsage,
  getOverlaySettings,
  isAutoStartEnabled,
  isIgnoringBatteryOptimizations,
  isMonitoring,
  openBatteryOptimizationSettings,
  openOverlayPermissionSettings,
  requestNotificationPermission,
  setAutoStartEnabled,
  setOverlayDarkText,
  setOverlayEnabled,
  setOverlayOffset,
  startMonitoring,
  stopMonitoring,
  subscribeSpeed,
  type DailyUsage,
  type OverlaySettings,
  type SpeedSample,
} from './src/NetworkSpeedMonitor';
import {SpeedChart} from './src/SpeedChart';

const OFFSET_STEP = 10;
const HISTORY_LEN = 40;

type HistoryPoint = {download: number; upload: number};

function Switch({
  value,
  onPress,
}: {
  value: boolean;
  onPress: () => void;
}): React.JSX.Element {
  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{checked: value}}
      onPress={onPress}
      style={[styles.switchTrack, value && styles.switchTrackOn]}>
      <View style={[styles.switchThumb, value && styles.switchThumbOn]} />
    </Pressable>
  );
}

function App(): React.JSX.Element {
  const [running, setRunning] = useState(false);
  const [busy, setBusy] = useState(false);
  const [batteryOk, setBatteryOk] = useState(true);
  const [autoStart, setAutoStart] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [overlay, setOverlay] = useState<OverlaySettings>({
    enabled: false,
    permitted: false,
    offset: 130,
    darkText: false,
  });
  const [speed, setSpeed] = useState<SpeedSample>({
    download: 0,
    upload: 0,
    total: 0,
    onWifi: false,
    signal: null,
  });
  const [usage, setUsage] = useState<DailyUsage | null>(null);
  const [history, setHistory] = useState<HistoryPoint[]>([]);
  const [peakDown, setPeakDown] = useState(0);
  const [peakUp, setPeakUp] = useState(0);
  const [sessionSeconds, setSessionSeconds] = useState(0);
  const sessionStartedAt = useRef<number | null>(null);

  const refreshState = useCallback(async () => {
    if (Platform.OS !== 'android') {
      return;
    }
    try {
      const [active, battery, overlaySettings, dailyUsage, boot] =
        await Promise.all([
          isMonitoring(),
          isIgnoringBatteryOptimizations(),
          getOverlaySettings(),
          getDailyUsage(),
          isAutoStartEnabled(),
        ]);
      setRunning(active);
      setBatteryOk(battery);
      setOverlay(overlaySettings);
      setUsage(dailyUsage);
      setAutoStart(boot);
      if (active && sessionStartedAt.current == null) {
        sessionStartedAt.current = Date.now();
      }
      if (!active) {
        sessionStartedAt.current = null;
        setSessionSeconds(0);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to read monitor state');
    }
  }, []);

  useEffect(() => {
    refreshState();
    let speedTicks = 0;
    const unsubscribe = subscribeSpeed(sample => {
      setSpeed(sample);
      setRunning(true);
      if (sessionStartedAt.current == null) {
        sessionStartedAt.current = Date.now();
      }
      setPeakDown(prev => Math.max(prev, sample.download));
      setPeakUp(prev => Math.max(prev, sample.upload));
      setHistory(prev => {
        const next = [...prev, {download: sample.download, upload: sample.upload}];
        if (next.length > HISTORY_LEN) {
          next.shift();
        }
        return next;
      });
      speedTicks += 1;
      if (speedTicks % 10 === 0) {
        getDailyUsage()
          .then(setUsage)
          .catch(() => {});
      }
    });
    const appStateSub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        refreshState();
      }
    });
    return () => {
      unsubscribe();
      appStateSub.remove();
    };
  }, [refreshState]);

  useEffect(() => {
    if (!running) {
      return;
    }
    const id = setInterval(() => {
      if (sessionStartedAt.current != null) {
        setSessionSeconds(
          Math.floor((Date.now() - sessionStartedAt.current) / 1000),
        );
      }
    }, 1000);
    return () => clearInterval(id);
  }, [running]);

  const averages = useMemo(() => {
    if (history.length === 0) {
      return {download: 0, upload: 0};
    }
    const down = history.reduce((s, p) => s + p.download, 0) / history.length;
    const up = history.reduce((s, p) => s + p.upload, 0) / history.length;
    return {download: down, upload: up};
  }, [history]);

  const chartDown = useMemo(() => history.map(h => h.download), [history]);
  const chartUp = useMemo(() => history.map(h => h.upload), [history]);

  const overlayActive = overlay.enabled && overlay.permitted;
  const connectionLabel = speed.onWifi ? 'Wi‑Fi' : 'Mobile';
  const signalLabel =
    speed.signal == null || speed.signal < 0 ? '—' : `${speed.signal}%`;

  const onToggleOverlay = async () => {
    setError(null);
    try {
      if (!overlay.enabled && !overlay.permitted) {
        await setOverlayEnabled(true);
        await openOverlayPermissionSettings();
        return;
      }
      await setOverlayEnabled(!overlay.enabled);
      await refreshState();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to change overlay');
    }
  };

  const onMoveOverlay = async (delta: number) => {
    try {
      const next = await setOverlayOffset(overlay.offset + delta);
      setOverlay(prev => ({...prev, offset: next}));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to move overlay');
    }
  };

  const onTextColor = async (dark: boolean) => {
    try {
      await setOverlayDarkText(dark);
      setOverlay(prev => ({...prev, darkText: dark}));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to change text color');
    }
  };

  const onToggleAutoStart = async () => {
    try {
      const next = !autoStart;
      await setAutoStartEnabled(next);
      setAutoStart(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to change auto-start');
    }
  };

  const onClearUsage = () => {
    Alert.alert(
      'Clear usage history?',
      'This removes all saved daily Mobile and Wi‑Fi totals. Today will start from zero.',
      [
        {text: 'Cancel', style: 'cancel'},
        {
          text: 'Clear',
          style: 'destructive',
          onPress: async () => {
            try {
              await clearDailyUsage();
              setUsage(await getDailyUsage());
            } catch (e) {
              setError(e instanceof Error ? e.message : 'Unable to clear usage');
            }
          },
        },
      ],
    );
  };

  const onToggle = async () => {
    if (Platform.OS !== 'android') {
      setError('Status-bar network speed is only available on Android.');
      return;
    }

    setBusy(true);
    setError(null);
    try {
      if (running) {
        await stopMonitoring();
        setRunning(false);
        setSpeed({
          download: 0,
          upload: 0,
          total: 0,
          onWifi: false,
          signal: null,
        });
        sessionStartedAt.current = null;
        setSessionSeconds(0);
        setHistory([]);
      } else {
        const allowed = await requestNotificationPermission();
        if (!allowed) {
          setError(
            'Notification permission is required to show speed in the status bar.',
          );
          return;
        }
        setPeakDown(0);
        setPeakUp(0);
        setHistory([]);
        await startMonitoring();
        sessionStartedAt.current = Date.now();
        setRunning(true);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Unable to toggle monitoring');
    } finally {
      setBusy(false);
      refreshState();
    }
  };

  const onBattery = async () => {
    try {
      await openBatteryOptimizationSettings();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : 'Unable to open battery settings',
      );
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" backgroundColor="#071018" />
      <ScrollView
        contentContainerStyle={styles.container}
        showsVerticalScrollIndicator={false}>
        <View style={styles.headerRow}>
          <View>
            <Text style={styles.brand}>Net Speed</Text>
            <Text style={styles.subtitle}>Realtime meter for Pixel</Text>
          </View>
          <View
            style={[
              styles.statusPill,
              running ? styles.statusPillOn : styles.statusPillOff,
            ]}>
            <View
              style={[
                styles.statusDot,
                running ? styles.statusDotOn : styles.statusDotOff,
              ]}
            />
            <Text style={styles.statusPillText}>
              {running ? 'Live' : 'Idle'}
            </Text>
          </View>
        </View>

        <View style={styles.hero}>
          <View style={styles.heroMeta}>
            <View style={styles.metaChip}>
              <Text style={styles.metaChipText}>{connectionLabel}</Text>
            </View>
            <View style={styles.metaChip}>
              <Text style={styles.metaChipText}>Signal {signalLabel}</Text>
            </View>
            {running ? (
              <View style={styles.metaChip}>
                <Text style={styles.metaChipText}>
                  {formatDuration(sessionSeconds)}
                </Text>
              </View>
            ) : null}
          </View>

          <View style={styles.meterRow}>
            <View style={styles.meterBlock}>
              <Text style={styles.meterLabel}>Download</Text>
              <Text style={[styles.meterValue, styles.downColor]}>
                {formatSpeed(speed.download)}
              </Text>
            </View>
            <View style={styles.meterBlock}>
              <Text style={styles.meterLabel}>Upload</Text>
              <Text style={[styles.meterValue, styles.upColor]}>
                {formatSpeed(speed.upload)}
              </Text>
            </View>
          </View>

          <View style={styles.chartCard}>
            <View style={styles.chartLegend}>
              <View style={styles.legendItem}>
                <View style={[styles.legendDot, styles.downColorBg]} />
                <Text style={styles.legendText}>Down</Text>
              </View>
              <View style={styles.legendItem}>
                <View style={[styles.legendDot, styles.upColorBg]} />
                <Text style={styles.legendText}>Up</Text>
              </View>
              <Text style={styles.chartHint}>Last {HISTORY_LEN}s</Text>
            </View>
            {history.length > 1 ? (
              <SpeedChart download={chartDown} upload={chartUp} />
            ) : (
              <View style={styles.chartEmpty}>
                <Text style={styles.chartEmptyText}>
                  {running
                    ? 'Collecting samples…'
                    : 'Start monitoring to see the live chart'}
                </Text>
              </View>
            )}
          </View>

          <View style={styles.statsGrid}>
            <View style={styles.statCell}>
              <Text style={styles.statLabel}>Peak ↓</Text>
              <Text style={styles.statValue}>{formatSpeed(peakDown)}</Text>
            </View>
            <View style={styles.statCell}>
              <Text style={styles.statLabel}>Peak ↑</Text>
              <Text style={styles.statValue}>{formatSpeed(peakUp)}</Text>
            </View>
            <View style={styles.statCell}>
              <Text style={styles.statLabel}>Avg ↓</Text>
              <Text style={styles.statValue}>
                {formatSpeed(averages.download)}
              </Text>
            </View>
            <View style={styles.statCell}>
              <Text style={styles.statLabel}>Avg ↑</Text>
              <Text style={styles.statValue}>
                {formatSpeed(averages.upload)}
              </Text>
            </View>
          </View>
        </View>

        <Pressable
          accessibilityRole="button"
          disabled={busy}
          onPress={onToggle}
          style={({pressed}) => [
            styles.primaryButton,
            running && styles.primaryButtonStop,
            pressed && styles.pressed,
            busy && styles.disabled,
          ]}>
          {busy ? (
            <ActivityIndicator color="#071018" />
          ) : (
            <Text style={styles.primaryButtonText}>
              {running ? 'Stop monitoring' : 'Start monitoring'}
            </Text>
          )}
        </Pressable>

        <Text style={styles.sectionTitle}>Status bar</Text>
        <View style={styles.card}>
          <View style={styles.rowBetween}>
            <View style={styles.rowText}>
              <Text style={styles.rowTitle}>Show next to Wi‑Fi icon</Text>
              <Text style={styles.rowSub}>
                {overlay.enabled && !overlay.permitted
                  ? 'Grant “Display over other apps”'
                  : 'One-line speed overlay on the status bar'}
              </Text>
            </View>
            <Switch value={overlayActive} onPress={onToggleOverlay} />
          </View>

          {overlayActive ? (
            <>
              <View style={styles.divider} />
              <View style={styles.nudgeRow}>
                <Pressable
                  onPress={() => onMoveOverlay(OFFSET_STEP)}
                  style={({pressed}) => [
                    styles.nudgeButton,
                    pressed && styles.pressed,
                  ]}>
                  <Text style={styles.nudgeText}>◀ Left</Text>
                </Pressable>
                <Text style={styles.nudgeValue}>{overlay.offset} dp</Text>
                <Pressable
                  onPress={() => onMoveOverlay(-OFFSET_STEP)}
                  style={({pressed}) => [
                    styles.nudgeButton,
                    pressed && styles.pressed,
                  ]}>
                  <Text style={styles.nudgeText}>Right ▶</Text>
                </Pressable>
              </View>
              <View style={styles.colorRow}>
                <Text style={styles.rowSub}>Text color</Text>
                <View style={styles.segment}>
                  {[
                    {label: 'White', dark: false},
                    {label: 'Black', dark: true},
                  ].map(option => {
                    const selected = overlay.darkText === option.dark;
                    return (
                      <Pressable
                        key={option.label}
                        onPress={() => onTextColor(option.dark)}
                        style={[
                          styles.segmentItem,
                          selected && styles.segmentItemOn,
                        ]}>
                        <Text
                          style={[
                            styles.segmentText,
                            selected && styles.segmentTextOn,
                          ]}>
                          {option.label}
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>
              </View>
              <View style={styles.previewRow}>
                <Text style={styles.rowSub}>Preview</Text>
                <View
                  style={[
                    styles.previewBar,
                    overlay.darkText && styles.previewBarLight,
                  ]}>
                  <Text
                    style={[
                      styles.previewValue,
                      overlay.darkText && styles.previewDark,
                    ]}>
                    1.2MB/s
                  </Text>
                </View>
              </View>
            </>
          ) : null}
        </View>

        <Text style={styles.sectionTitle}>Reliability</Text>
        <View style={styles.card}>
          <View style={styles.rowBetween}>
            <View style={styles.rowText}>
              <Text style={styles.rowTitle}>Start on boot</Text>
              <Text style={styles.rowSub}>
                Resume monitoring after phone restart
              </Text>
            </View>
            <Switch value={autoStart} onPress={onToggleAutoStart} />
          </View>
          {!batteryOk ? (
            <>
              <View style={styles.divider} />
              <Pressable
                onPress={onBattery}
                style={({pressed}) => [
                  styles.inlineAction,
                  pressed && styles.pressed,
                ]}>
                <Text style={styles.inlineActionText}>
                  Allow unrestricted battery use
                </Text>
                <Text style={styles.inlineActionChevron}>›</Text>
              </Pressable>
            </>
          ) : null}
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitleInline}>Daily data usage</Text>
          <Pressable onPress={onClearUsage} hitSlop={8}>
            <Text style={styles.clearLink}>Clear</Text>
          </Pressable>
        </View>
        <View style={styles.cardFlush}>
          <Text style={styles.usageHint}>
            Resets at midnight. Keep monitoring on to record Mobile and Wi‑Fi.
          </Text>
          <View style={styles.tableHeader}>
            <Text style={[styles.th, styles.colDate]}>Date</Text>
            <Text style={[styles.th, styles.colNum]}>Mobile</Text>
            <Text style={[styles.th, styles.colNum]}>WiFi</Text>
            <Text style={[styles.th, styles.colNum]}>Total</Text>
          </View>
          {(usage?.days ?? []).map((day, index) => (
            <View
              key={day.dateKey}
              style={[styles.tableRow, index % 2 === 1 && styles.tableRowAlt]}>
              <Text
                style={[
                  styles.tdDate,
                  styles.colDate,
                  day.isToday && styles.tdToday,
                ]}
                numberOfLines={1}>
                {day.label}
              </Text>
              <Text style={[styles.td, styles.colNum]} numberOfLines={1}>
                {formatData(day.mobile)}
              </Text>
              <Text style={[styles.td, styles.colNum]} numberOfLines={1}>
                {formatData(day.wifi)}
              </Text>
              <Text
                style={[styles.td, styles.colNum, styles.tdTotal]}
                numberOfLines={1}>
                {formatData(day.total)}
              </Text>
            </View>
          ))}
          {usage?.month ? (
            <View style={[styles.tableRow, styles.tableRowMonth]}>
              <Text style={[styles.tdDate, styles.colDate, styles.tdMonth]}>
                {usage.month.label}
              </Text>
              <Text style={[styles.td, styles.colNum, styles.tdMonth]}>
                {formatData(usage.month.mobile)}
              </Text>
              <Text style={[styles.td, styles.colNum, styles.tdMonth]}>
                {formatData(usage.month.wifi)}
              </Text>
              <Text style={[styles.td, styles.colNum, styles.tdMonth]}>
                {formatData(usage.month.total)}
              </Text>
            </View>
          ) : null}
          {!usage?.days?.length ? (
            <Text style={styles.usageEmpty}>No usage recorded yet</Text>
          ) : null}
        </View>

        {error ? <Text style={styles.error}>{error}</Text> : null}
        <Text style={styles.footer}>Net Speed · Pixel status bar meter</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const mono = Platform.select({
  ios: 'Menlo',
  android: 'monospace',
  default: 'monospace',
});

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: '#071018',
  },
  container: {
    flexGrow: 1,
    paddingHorizontal: 20,
    paddingTop: 28,
    paddingBottom: 40,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 22,
  },
  brand: {
    fontFamily: mono,
    fontSize: 28,
    fontWeight: '700',
    color: '#F4F7FB',
    letterSpacing: -0.5,
  },
  subtitle: {
    marginTop: 4,
    fontSize: 14,
    color: '#7F93AB',
  },
  statusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderWidth: 1,
    gap: 6,
  },
  statusPillOn: {
    borderColor: '#2A5A45',
    backgroundColor: '#0E2A1F',
  },
  statusPillOff: {
    borderColor: '#243044',
    backgroundColor: '#101826',
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 4,
  },
  statusDotOn: {
    backgroundColor: '#3DDC97',
  },
  statusDotOff: {
    backgroundColor: '#5A6B80',
  },
  statusPillText: {
    color: '#D7E2EF',
    fontSize: 12,
    fontWeight: '600',
  },
  hero: {
    borderWidth: 1,
    borderColor: '#1A2738',
    backgroundColor: '#0C1520',
    padding: 16,
    marginBottom: 16,
  },
  heroMeta: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 16,
  },
  metaChip: {
    borderWidth: 1,
    borderColor: '#243044',
    paddingHorizontal: 10,
    paddingVertical: 5,
    backgroundColor: '#101C2A',
  },
  metaChipText: {
    color: '#A9BBD0',
    fontSize: 12,
    fontWeight: '600',
  },
  meterRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 16,
  },
  meterBlock: {
    flex: 1,
  },
  meterLabel: {
    fontSize: 11,
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: '#6F849F',
    marginBottom: 6,
  },
  meterValue: {
    fontFamily: mono,
    fontSize: 24,
    fontWeight: '700',
  },
  downColor: {
    color: '#3DDC97',
  },
  upColor: {
    color: '#5B8CFF',
  },
  downColorBg: {
    backgroundColor: '#3DDC97',
  },
  upColorBg: {
    backgroundColor: '#5B8CFF',
  },
  chartCard: {
    borderWidth: 1,
    borderColor: '#1A2738',
    backgroundColor: '#0A121C',
    padding: 12,
    marginBottom: 14,
  },
  chartLegend: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
    gap: 12,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  legendDot: {
    width: 8,
    height: 8,
    borderRadius: 2,
  },
  legendText: {
    color: '#8FA3BF',
    fontSize: 12,
  },
  chartHint: {
    marginLeft: 'auto',
    color: '#5F738A',
    fontSize: 11,
  },
  chartEmpty: {
    height: 88,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chartEmptyText: {
    color: '#5F738A',
    fontSize: 13,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  statCell: {
    width: '48%',
    flexGrow: 1,
    borderWidth: 1,
    borderColor: '#1A2738',
    backgroundColor: '#0A121C',
    paddingVertical: 10,
    paddingHorizontal: 12,
  },
  statLabel: {
    color: '#6F849F',
    fontSize: 11,
    marginBottom: 4,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  statValue: {
    color: '#E8F1FF',
    fontFamily: mono,
    fontSize: 14,
    fontWeight: '700',
  },
  primaryButton: {
    backgroundColor: '#3DDC97',
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  primaryButtonStop: {
    backgroundColor: '#FF7A6A',
  },
  primaryButtonText: {
    color: '#071018',
    fontSize: 16,
    fontWeight: '700',
  },
  sectionTitle: {
    marginTop: 22,
    marginBottom: 10,
    color: '#8FA3BF',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  sectionHeader: {
    marginTop: 22,
    marginBottom: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sectionTitleInline: {
    color: '#8FA3BF',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  clearLink: {
    color: '#FF8B7C',
    fontSize: 13,
    fontWeight: '600',
  },
  card: {
    borderWidth: 1,
    borderColor: '#1A2738',
    backgroundColor: '#0C1520',
    padding: 16,
  },
  cardFlush: {
    borderWidth: 1,
    borderColor: '#1A2738',
    backgroundColor: '#0C1520',
    overflow: 'hidden',
  },
  rowBetween: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  rowText: {
    flex: 1,
    paddingRight: 12,
  },
  rowTitle: {
    color: '#E8F1FF',
    fontSize: 15,
    fontWeight: '700',
  },
  rowSub: {
    marginTop: 4,
    color: '#6F849F',
    fontSize: 13,
    lineHeight: 18,
  },
  switchTrack: {
    width: 52,
    height: 30,
    borderRadius: 15,
    backgroundColor: '#2B3B55',
    padding: 3,
    justifyContent: 'center',
  },
  switchTrackOn: {
    backgroundColor: '#3DDC97',
  },
  switchThumb: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#E8F1FF',
  },
  switchThumbOn: {
    alignSelf: 'flex-end',
    backgroundColor: '#071018',
  },
  divider: {
    height: 1,
    backgroundColor: '#1A2738',
    marginVertical: 14,
  },
  nudgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  nudgeButton: {
    borderWidth: 1,
    borderColor: '#2B3B55',
    paddingVertical: 10,
    paddingHorizontal: 16,
  },
  nudgeText: {
    color: '#E8F1FF',
    fontSize: 14,
    fontWeight: '600',
  },
  nudgeValue: {
    color: '#8FA3BF',
    fontSize: 13,
    fontFamily: mono,
  },
  colorRow: {
    marginTop: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  segment: {
    flexDirection: 'row',
    borderWidth: 1,
    borderColor: '#2B3B55',
  },
  segmentItem: {
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  segmentItemOn: {
    backgroundColor: '#3DDC97',
  },
  segmentText: {
    color: '#E8F1FF',
    fontSize: 13,
    fontWeight: '600',
  },
  segmentTextOn: {
    color: '#071018',
  },
  previewRow: {
    marginTop: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  previewBar: {
    minWidth: 100,
    height: 30,
    backgroundColor: '#000000',
    alignItems: 'flex-end',
    justifyContent: 'center',
    paddingHorizontal: 12,
  },
  previewBarLight: {
    backgroundColor: '#F1F3F6',
  },
  previewValue: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
    fontFamily: Platform.select({
      android: 'sans-serif-condensed',
      default: undefined,
    }),
  },
  previewDark: {
    color: '#000000',
  },
  inlineAction: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  inlineActionText: {
    color: '#E8F1FF',
    fontSize: 14,
    fontWeight: '600',
  },
  inlineActionChevron: {
    color: '#8FA3BF',
    fontSize: 22,
    lineHeight: 22,
  },
  usageHint: {
    paddingHorizontal: 14,
    paddingTop: 12,
    paddingBottom: 10,
    color: '#6F849F',
    fontSize: 12,
    lineHeight: 18,
  },
  tableHeader: {
    flexDirection: 'row',
    backgroundColor: '#121C2A',
    paddingVertical: 10,
    paddingHorizontal: 10,
  },
  tableRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 11,
    paddingHorizontal: 10,
    backgroundColor: '#0C1520',
  },
  tableRowAlt: {
    backgroundColor: '#0A121C',
  },
  tableRowMonth: {
    backgroundColor: '#121C2A',
    borderTopWidth: 1,
    borderTopColor: '#1A2738',
  },
  th: {
    color: '#8FA3BF',
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  td: {
    color: '#E8F1FF',
    fontSize: 12,
    fontFamily: mono,
  },
  tdDate: {
    color: '#3DDC97',
    fontSize: 12,
    fontWeight: '700',
  },
  tdToday: {
    color: '#FFFFFF',
  },
  tdTotal: {
    fontWeight: '700',
  },
  tdMonth: {
    color: '#3DDC97',
    fontWeight: '700',
  },
  colDate: {
    flex: 1.35,
    paddingRight: 6,
  },
  colNum: {
    flex: 1,
    textAlign: 'right',
  },
  usageEmpty: {
    padding: 16,
    color: '#6F849F',
    fontSize: 13,
  },
  error: {
    marginTop: 16,
    color: '#FF8B7C',
    fontSize: 14,
    lineHeight: 20,
  },
  footer: {
    marginTop: 28,
    textAlign: 'center',
    color: '#45566B',
    fontSize: 12,
  },
  pressed: {
    opacity: 0.85,
  },
  disabled: {
    opacity: 0.6,
  },
});

export default App;

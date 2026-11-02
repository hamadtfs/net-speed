import React, {useMemo} from 'react';
import {StyleSheet, View} from 'react-native';

type Props = {
  download: number[];
  upload: number[];
  height?: number;
};

/**
 * Lightweight dual-series sparkline for recent download / upload samples.
 */
export function SpeedChart({download, upload, height = 88}: Props): React.JSX.Element {
  const max = useMemo(() => {
    const all = [...download, ...upload];
    const peak = Math.max(1, ...all);
    return peak;
  }, [download, upload]);

  const count = Math.max(download.length, upload.length, 1);

  return (
    <View style={[styles.wrap, {height}]}>
      <View style={styles.row}>
        {Array.from({length: count}).map((_, i) => {
          const d = download[i] ?? 0;
          const u = upload[i] ?? 0;
          const dH = Math.max(2, (d / max) * (height - 8));
          const uH = Math.max(2, (u / max) * (height - 8));
          return (
            <View key={i} style={styles.barGroup}>
              <View style={[styles.bar, styles.down, {height: dH}]} />
              <View style={[styles.bar, styles.up, {height: uH}]} />
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    width: '100%',
    justifyContent: 'flex-end',
    overflow: 'hidden',
  },
  row: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 2,
  },
  barGroup: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'center',
    gap: 1,
  },
  bar: {
    flex: 1,
    maxWidth: 4,
    borderTopLeftRadius: 2,
    borderTopRightRadius: 2,
  },
  down: {
    backgroundColor: '#3DDC97',
  },
  up: {
    backgroundColor: '#5B8CFF',
  },
});

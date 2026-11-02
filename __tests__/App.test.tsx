/**
 * @format
 */

import React from 'react';
import {jest, test} from '@jest/globals';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';

jest.mock('../src/NetworkSpeedMonitor', () => ({
  formatSpeed: (n: number) => `${n} B/s`,
  formatData: (n: number) => `${n} B`,
  formatDuration: (n: number) => `${n}s`,
  getDailyUsage: jest.fn(async () => ({
    days: [
      {
        dateKey: '2026-10-22',
        label: 'Today',
        isToday: true,
        mobile: 0,
        wifi: 0,
        total: 0,
      },
    ],
    month: {label: 'This Month', mobile: 0, wifi: 0, total: 0},
  })),
  clearDailyUsage: jest.fn(async () => true),
  isAutoStartEnabled: jest.fn(async () => false),
  setAutoStartEnabled: jest.fn(async () => true),
  getOverlaySettings: jest.fn(async () => ({
    enabled: false,
    permitted: false,
    offset: 130,
    darkText: false,
  })),
  setOverlayDarkText: jest.fn(async () => true),
  openOverlayPermissionSettings: jest.fn(async () => true),
  setOverlayEnabled: jest.fn(async () => true),
  setOverlayOffset: jest.fn(async (n: number) => n),
  isIgnoringBatteryOptimizations: jest.fn(async () => true),
  isMonitoring: jest.fn(async () => false),
  openBatteryOptimizationSettings: jest.fn(async () => true),
  requestNotificationPermission: jest.fn(async () => true),
  startMonitoring: jest.fn(async () => true),
  stopMonitoring: jest.fn(async () => true),
  subscribeSpeed: jest.fn(() => () => {}),
}));

test('renders correctly', async () => {
  await ReactTestRenderer.act(() => {
    ReactTestRenderer.create(<App />);
  });
});

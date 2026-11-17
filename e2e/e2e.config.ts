import { mobile } from '@e2e-dev/mobile';
import type { E2EConfig } from 'e2e';

const bundleId = 'com.icecream.kwklasplus';

export default {
  targets: [
    { name: 'android', engine: mobile({ platform: 'android', device: process.env.E2E_ANDROID_DEVICE }), app: { bundleId } },
    { name: 'ios', engine: mobile({ platform: 'ios', device: process.env.E2E_IOS_DEVICE }), app: { bundleId } },
  ],
  workers: 1,
  retries: 0,
  timeout: 180_000,
  trace: 'retain-on-failure',
  video: 'off',
} satisfies E2EConfig;

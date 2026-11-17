import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

import { signInDemo } from './demo-session';

test('fresh install: onboarding, demo sign-in, WebView, session restore and logout', async ({ app, screen, platform }) => {
  const { onboarding, studentId, menu, waitForControl } = await signInDemo({ app, screen, platform }, { checkInvalidPassword: true });

  await app.restart();
  await waitForControl(menu, 45_000);
  if (platform === 'android') {
    await menu.tap();
    await screen.getByText('로그아웃').tap();
    await expect(screen.getByText('정말 로그아웃할까요?')).toBeVisible();
    await screen.getByText('취소').tap();
    await expect(screen.getByText('정말 로그아웃할까요?')).toBeHidden();
  }

  await menu.tap();
  await screen.getByText('로그아웃').tap();
  await expect(screen.getByText('정말 로그아웃할까요?')).toBeVisible();
  const confirm = platform === 'ios' ? screen.getByRole('button', '확인') : screen.getByText('확인');
  await confirm.tap();
  await expect(platform === 'ios' ? studentId : onboarding).toBeVisible({ timeout: 30_000 });

  await app.restart();
  await waitForControl(onboarding);
});

import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

const demoId = '0000000000';
const demoPassword = 'KlasplusDemo!123';

test('fresh install: onboarding, demo sign-in, WebView, session restore and logout', async ({ app, screen, platform }) => {
  const onboarding = platform === 'ios' ? screen.getByRole('button', '로그인') : screen.getByTestId('login_start');
  const studentId = platform === 'ios' ? screen.getByRole('textbox', '학번') : screen.getByTestId('login_student_id');
  const password = platform === 'ios' ? screen.getByRole('textbox', '비밀번호') : screen.getByTestId('login_password');
  const submit = platform === 'ios' ? screen.getByRole('button', '로그인하기') : screen.getByTestId('login_submit');
  const menu = screen.getByRole('button', '메뉴 열기');

  await app.clearState();
  await expect(onboarding).toBeVisible({ timeout: 30_000 });
  await onboarding.tap();
  await expect(studentId).toBeVisible();
  // 열 번째 숫자에서 학번 필드가 사라지므로 입력 검증과 화면 전환을 나눠 확인한다.
  await studentId.fill(demoId.slice(0, -1));
  await studentId.press(demoId.slice(-1));
  await expect(password).toBeVisible();

  // Android의 UI 드라이버는 Compose의 가려진 입력을 검증할 수 없다. 가상 계정에서만 표시한다.
  if (platform === 'android') await screen.getByLabel('비밀번호 표시').tap();
  await password.fill('InvalidDemoPassword');
  await submit.tap();
  await expect(screen.getByText('학번 또는 비밀번호를 확인해 주세요.')).toBeVisible();

  await password.fill(demoPassword);
  await submit.tap();
  await expect(menu).toBeVisible({ timeout: 45_000 });
  await expect(screen.getByText('컴퓨터프로그래밍')).toBeVisible({ timeout: 30_000 });

  await app.restart();
  await expect(menu).toBeVisible({ timeout: 45_000 });
  if (platform === 'android') {
    await menu.tap();
    await screen.getByText('로그아웃').tap();
    await expect(screen.getByText('정말 로그아웃할까요?')).toBeVisible();
    await screen.getByText('취소').tap();
    await expect(menu).toBeVisible();
  }

  await menu.tap();
  await screen.getByText('로그아웃').tap();
  await expect(screen.getByText('정말 로그아웃할까요?')).toBeVisible();
  const confirm = platform === 'ios' ? screen.getByRole('button', '확인') : screen.getByText('확인');
  await confirm.tap();
  await expect(platform === 'ios' ? studentId : onboarding).toBeVisible({ timeout: 30_000 });

  await app.restart();
  await expect(onboarding).toBeVisible({ timeout: 30_000 });
});

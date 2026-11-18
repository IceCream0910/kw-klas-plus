import { expect, type Locator, type TestFixtures } from 'e2e';

type AppFixtures = Pick<TestFixtures, 'app' | 'screen' | 'platform'>;
const demoId = '0000000000';
const demoPassword = 'KlasplusDemo!123';

export async function openLoginForm({ app, screen, platform }: AppFixtures) {
  const ios = platform === 'ios';
  const onboarding = ios ? screen.getByRole('button', '로그인') : screen.getByTestId('login_start');
  const studentId = ios ? screen.getByRole('textbox', '학번') : screen.getByTestId('login_student_id');
  const password = ios ? screen.getByRole('textbox', '비밀번호') : screen.getByTestId('login_password');
  const submit = ios ? screen.getByRole('button', '로그인하기') : screen.getByTestId('login_submit');
  const menu = screen.getByRole('button', '메뉴 열기');
  const skipPasswordSave = screen.getByRole('button', /^(지금 안 함|Not Now|Don[’']t Save)$/);

  async function waitForControl(control: Locator, timeout = 30_000) {
    if (ios) {
      await expect(control.or(skipPasswordSave).first()).toBeVisible({ timeout });
      if (await skipPasswordSave.isVisible()) await skipPasswordSave.tap();
    }
    await expect(control).toBeVisible({ timeout });
  }

  await app.clearState();
  await waitForControl(onboarding);
  try {
    await onboarding.tap();
  } catch (error) {
    if (platform !== 'ios' || !(error instanceof Error) ||
        !error.message.includes('no established screen-coordinate origin')) throw error;
    await screen.tree();
    await expect(onboarding).toBeVisible({ timeout: 30_000 });
    await onboarding.tap();
  }
  await expect(studentId).toBeVisible({ timeout: 30_000 });
  return { onboarding, studentId, password, submit, menu, waitForControl };
}

export async function signInDemo(fixtures: AppFixtures, { checkInvalidPassword = false } = {}) {
  const { screen, platform } = fixtures;
  const controls = await openLoginForm(fixtures);
  const { studentId, password, submit, menu, waitForControl } = controls;
  // 열 번째 숫자에서 학번 입력창이 사라지므로 입력과 화면 전환을 나눠 확인한다.
  await studentId.fill(demoId.slice(0, -1));
  if (checkInvalidPassword) {
    const next = platform === 'ios' ? screen.getByRole('button', '다음') : submit;
    await expect(next).toBeDisabled();
    await expect(password).toBeHidden();
  }
  await studentId.press(demoId.slice(-1));
  await expect(password).toBeVisible({ timeout: 15_000 });

  // Compose의 가려진 값을 드라이버에서 확인할 수 없어 데모 비밀번호만 표시한다.
  if (platform === 'android') await screen.getByLabel('비밀번호 표시').tap();
  if (checkInvalidPassword) {
    await password.fill('InvalidDemoPassword');
    await waitForControl(submit);
    await submit.tap();
    await expect(screen.getByText('학번 또는 비밀번호를 확인해 주세요.')).toBeVisible();
    if (platform === 'ios') await password.clear();
  }
  await password.fill(demoPassword);
  await waitForControl(submit);
  await submit.tap();
  await waitForControl(menu, 45_000);
  await expect(screen.getByText('컴퓨터프로그래밍')).toBeVisible({ timeout: 30_000 });
  return controls;
}

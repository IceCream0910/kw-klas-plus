import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';
import { openLoginForm, signInDemo } from './demo-session';

test('login help opens a native sheet and closing it returns to the student ID field', async ({ app, device, screen, platform }) => {
  const { studentId } = await openLoginForm({ app, screen, platform });
  const help = platform === 'ios'
    ? screen.getByRole('button', '로그인은 어떻게 처리되나요?')
    : screen.getByTestId('login_security_link');
  const close = platform === 'ios' ? screen.getByRole('button', '닫기') : screen.getByText('닫기');
  if (platform === 'android') await device.dismissKeyboard();
  await help.tap();
  await expect(screen.getByText(/학번과 비밀번호는 이 기기에서 학교 KLAS 서버로 직접 전달돼요/)).toBeVisible({ timeout: 30_000 });
  await close.tap();
  await expect(close).toBeHidden();
  await expect(studentId).toBeVisible();
});

test('native tabs switch between the feed and timetable, including after iOS rotation', async ({ app, device, screen, platform }) => {
  const { menu, waitForControl } = await signInDemo({ app, screen, platform });
  const ios = platform === 'ios';
  const timetableTab = ios ? screen.getByRole('button', '시간표') : screen.getByText('시간표');
  const feedTab = ios ? screen.getByRole('button', '피드') : screen.getByText('피드');
  const feedCourse = screen.getByText('컴퓨터프로그래밍');
  const timetableCourse = screen.getByRole('button', /^컴퓨터프로그래밍, .+부터 /).first();
  if (ios) {
    const home = screen.getByTestId('home_view');
    async function waitForOrientation(landscape: boolean) {
      await expect.poll(async () => {
        const frame = await home.boundingBox();
        return frame != null && (landscape ? frame.width > frame.height : frame.height > frame.width);
      }, { timeout: 15_000, message: landscape ? 'Home view must rotate to landscape' : 'Home view must return to portrait' }).toBe(true);
    }
    try {
      await device.setOrientation('landscape-left');
      await waitForOrientation(true);
      await expect(timetableTab).toBeVisible({ timeout: 15_000 });
      await waitForControl(menu);
    } finally {
      await device.setOrientation('portrait');
    }
    await waitForOrientation(false);
  }
  await timetableTab.tap();
  await expect(timetableCourse).toBeVisible({ timeout: 30_000 });
  await feedTab.tap();
  await expect(timetableCourse).toBeHidden();
  await expect(feedCourse).toBeVisible({ timeout: 30_000 });
});

test('choosing another semester in the native sheet updates the WebView term', async ({ app, screen, platform }) => {
  await signInDemo({ app, screen, platform });
  const timetableTab = platform === 'ios' ? screen.getByRole('button', '시간표') : screen.getByText('시간표');
  await timetableTab.tap();

  const termLabel = /^\d{4}(?:년도|년)?\s*[12]학기$/;
  const pickerButton = screen.getByRole('button', termLabel);
  await expect(pickerButton).toBeVisible({ timeout: 30_000 });
  const initial = (await pickerButton.textContent()).match(/\d+/g)?.map(Number);
  if (initial?.length !== 2) throw new Error('The timetable did not show the current semester.');
  const [year, semester] = initial;
  const previous = new RegExp(`^${semester === 2 ? year : year - 1}(?:년도|년)?\\s*${semester === 2 ? 1 : 2}학기$`);

  await pickerButton.tap();
  await expect(screen.getByText('학기 선택')).toBeVisible();
  await screen.getByText(previous).tap();
  await expect(screen.getByText('학기 선택')).toBeHidden();
  await expect(screen.getByRole('button', previous)).toBeVisible({ timeout: 30_000 });
});

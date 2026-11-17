import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';
import { signInDemo } from './demo-session';

test('demo lecture plays, pauses, seeks and the lecture list is usable again', { tags: ['live-media'] }, async ({ app, screen, platform }) => {
  await signInDemo({ app, screen, platform });
  const lectures = screen.getByText('온라인 강의').first();
  await lectures.tap();
  const lecture = screen.getByText('프로그래밍 시작하기').first();
  await expect(lecture).toBeVisible({ timeout: 30_000 });
  await lecture.tap();

  const control = (label: string) => platform === 'ios' ? screen.getByRole('button', label) : screen.getByLabel(label);
  const play = control('재생');
  const pause = control('일시정지');
  await expect(screen.getByText('02:00')).toBeVisible({ timeout: 45_000 });
  if (await pause.isVisible()) await pause.tap();
  await expect(play).toBeVisible();
  await play.tap();
  await expect(pause).toBeVisible();
  const elapsed = screen.getByText(/^(?!02:00)\d{2}:[0-5]\d$/).first();
  async function elapsedSeconds() {
    const [minutes, seconds] = (await elapsed.textContent()).split(':').map(Number);
    return minutes * 60 + seconds;
  }
  // 이미 재생한 구간으로 이동해 아직 받지 않은 원격 구간에 의존하지 않는다.
  await expect.poll(elapsedSeconds, { timeout: 30_000 }).toBeGreaterThanOrEqual(12);
  await pause.tap();
  await expect(play).toBeVisible();
  const before = await elapsedSeconds();
  await control('10초 뒤로').tap();
  await expect.poll(elapsedSeconds, { timeout: 15_000 }).toBeLessThanOrEqual(before - 9);
  await expect.poll(elapsedSeconds).toBeGreaterThanOrEqual(before - 11);

  const finish = platform === 'ios' ? control('학습 종료') : screen.getByText('학습 종료');
  await finish.tap();
  await expect(screen.getByText('정말 강의 수강을 종료할까요?')).toBeVisible();
  const confirm = platform === 'ios' ? control('확인') : screen.getByText('확인');
  await confirm.tap();
  await expect(finish).toBeHidden();
  if (platform === 'android') {
    await expect(lectures).toBeVisible({ timeout: 30_000 });
    await lectures.tap();
  }
  await expect(lecture).toBeVisible({ timeout: 30_000 });
});

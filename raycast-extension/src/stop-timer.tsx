import { showToast, Toast, closeMainWindow } from "@raycast/api";
import { fetchCurrentTimer, stopTimer, formatDurationShort } from "./api";

export default async function Command() {
  try {
    const timer = await fetchCurrentTimer();

    if (!timer) {
      await showToast({ style: Toast.Style.Failure, title: "No timer running" });
      return;
    }

    const entry = await stopTimer(timer.id);
    const duration = entry.durationMinutes
      ? `${Math.floor(entry.durationMinutes / 60)}h ${entry.durationMinutes % 60}m`
      : formatDurationShort(timer.startedAt);

    await showToast({
      style: Toast.Style.Success,
      title: "Timer stopped",
      message: `${timer.projectName} — ${duration}`,
    });
    await closeMainWindow();
  } catch (err) {
    await showToast({ style: Toast.Style.Failure, title: "Failed to stop timer", message: String(err) });
  }
}

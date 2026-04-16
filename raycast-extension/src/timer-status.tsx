import { MenuBarExtra, open, showToast, Toast } from "@raycast/api";
import { useEffect, useState } from "react";
import { fetchCurrentTimer, stopTimer, TimerStatus, formatDurationShort, formatDuration } from "./api";

export default function Command() {
  const [timer, setTimer] = useState<TimerStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        setTimer(await fetchCurrentTimer());
      } catch {
        // Silently fail for menu bar — don't spam toasts
      }
      setIsLoading(false);
    }
    load();
  }, []);

  const title = timer ? `⏱ ${formatDurationShort(timer.startedAt)}` : "⏱";

  return (
    <MenuBarExtra icon="extension-icon.png" title={title} isLoading={isLoading}>
      {timer ? (
        <>
          <MenuBarExtra.Item title={timer.projectName} />
          {timer.description && <MenuBarExtra.Item title={timer.description} subtitle="description" />}
          <MenuBarExtra.Item title={formatDuration(timer.startedAt)} subtitle="elapsed" />
          <MenuBarExtra.Separator />
          <MenuBarExtra.Item
            title="Stop Timer"
            shortcut={{ modifiers: ["cmd"], key: "s" }}
            onAction={async () => {
              try {
                await stopTimer(timer.id);
                setTimer(null);
                await showToast({ style: Toast.Style.Success, title: "Timer stopped" });
              } catch (err) {
                await showToast({ style: Toast.Style.Failure, title: "Failed", message: String(err) });
              }
            }}
          />
        </>
      ) : (
        <MenuBarExtra.Item title="No timer running" />
      )}
    </MenuBarExtra>
  );
}

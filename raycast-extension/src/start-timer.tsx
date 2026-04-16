import { Action, ActionPanel, Form, showToast, Toast, popToRoot } from "@raycast/api";
import { useEffect, useState } from "react";
import { fetchProjects, fetchCurrentTimer, startTimer, stopTimer, Project } from "./api";

export default function Command() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [runningTimerId, setRunningTimerId] = useState<number | null>(null);
  const [runningProjectName, setRunningProjectName] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const [projs, timer] = await Promise.all([fetchProjects(), fetchCurrentTimer()]);
        setProjects(projs);
        if (timer) {
          setRunningTimerId(timer.id);
          setRunningProjectName(timer.projectName);
        }
      } catch (err) {
        showToast({ style: Toast.Style.Failure, title: "Failed to load", message: String(err) });
      }
      setIsLoading(false);
    }
    load();
  }, []);

  async function handleSubmit(values: { projectId: string; description: string }) {
    const projectId = parseInt(values.projectId);
    if (!projectId) {
      showToast({ style: Toast.Style.Failure, title: "Select a project" });
      return;
    }

    try {
      // Stop running timer first if there is one
      if (runningTimerId) {
        await stopTimer(runningTimerId);
      }

      const entry = await startTimer(projectId, values.description || null);
      showToast({
        style: Toast.Style.Success,
        title: "Timer started",
        message: entry.projectName + (entry.description ? ` — ${entry.description}` : ""),
      });
      popToRoot();
    } catch (err) {
      showToast({ style: Toast.Style.Failure, title: "Failed to start timer", message: String(err) });
    }
  }

  return (
    <Form
      isLoading={isLoading}
      actions={
        <ActionPanel>
          <Action.SubmitForm title="Start Timer" onSubmit={handleSubmit} />
        </ActionPanel>
      }
    >
      {runningTimerId && (
        <Form.Description
          title="Currently Running"
          text={`⏱ ${runningProjectName} — will be stopped when you start a new timer`}
        />
      )}
      <Form.Dropdown id="projectId" title="Project" storeValue>
        {projects.map((p) => (
          <Form.Dropdown.Item
            key={p.id}
            value={String(p.id)}
            title={p.clientName ? `${p.name} (${p.clientName})` : p.name}
          />
        ))}
      </Form.Dropdown>
      <Form.TextField id="description" title="Description" placeholder="What are you working on?" />
    </Form>
  );
}

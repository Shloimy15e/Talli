import { getPreferenceValues } from "@raycast/api";

interface Preferences {
  serverUrl: string;
  apiToken: string;
}

export interface Project {
  id: number;
  name: string;
  clientId: number | null;
  clientName: string | null;
  status: string;
}

export interface TimerStatus {
  id: number;
  projectId: number;
  projectName: string;
  description: string | null;
  startedAt: string;
  running: boolean;
}

export interface TimeEntryResponse {
  id: number;
  projectId: number;
  projectName: string;
  startedAt: string;
  endedAt: string | null;
  durationMinutes: number | null;
  description: string | null;
  billable: boolean;
}

export interface ExpenseResponse {
  id: number;
  amount: number;
  category: string;
  vendor: string | null;
}

export async function talliFetch(path: string, options?: RequestInit): Promise<Response> {
  const { serverUrl, apiToken } = getPreferenceValues<Preferences>();
  const res = await fetch(`${serverUrl.replace(/\/+$/, "")}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiToken}`,
      ...(options?.headers || {}),
    },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res;
}

export async function fetchProjects(): Promise<Project[]> {
  const res = await talliFetch("/api/v1/projects");
  return res.json();
}

export async function fetchCurrentTimer(): Promise<TimerStatus | null> {
  const res = await talliFetch("/api/v1/time/current");
  if (res.status === 204) return null;
  return res.json();
}

export async function startTimer(projectId: number, description: string | null): Promise<TimeEntryResponse> {
  const res = await talliFetch("/api/v1/time/start", {
    method: "POST",
    body: JSON.stringify({ projectId, description }),
  });
  return res.json();
}

export async function stopTimer(id: number): Promise<TimeEntryResponse> {
  const res = await talliFetch(`/api/v1/time/${id}/stop`, { method: "POST" });
  return res.json();
}

export async function createExpense(data: {
  projectId?: number | null;
  amount: number;
  category: string;
  vendor?: string | null;
  description?: string | null;
}): Promise<ExpenseResponse> {
  const res = await talliFetch("/api/v1/expenses", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.json();
}

export function formatDuration(startedAt: string): string {
  const start = new Date(startedAt);
  const now = new Date();
  const diff = Math.floor((now.getTime() - start.getTime()) / 1000);
  const h = Math.floor(diff / 3600);
  const m = Math.floor((diff % 3600) / 60);
  const s = diff % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

export function formatDurationShort(startedAt: string): string {
  const start = new Date(startedAt);
  const now = new Date();
  const diffMin = Math.floor((now.getTime() - start.getTime()) / 60000);
  const h = Math.floor(diffMin / 60);
  const m = diffMin % 60;
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

export const EXPENSE_CATEGORIES = [
  "software",
  "hardware",
  "travel",
  "meals",
  "contractors",
  "office",
  "marketing",
  "taxes",
  "other",
] as const;

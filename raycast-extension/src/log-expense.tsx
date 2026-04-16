import { Action, ActionPanel, Form, showToast, Toast, popToRoot } from "@raycast/api";
import { useEffect, useState } from "react";
import { fetchProjects, createExpense, Project, EXPENSE_CATEGORIES } from "./api";

export default function Command() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        setProjects(await fetchProjects());
      } catch (err) {
        showToast({ style: Toast.Style.Failure, title: "Failed to load projects", message: String(err) });
      }
      setIsLoading(false);
    }
    load();
  }, []);

  async function handleSubmit(values: {
    amount: string;
    category: string;
    vendor: string;
    projectId: string;
    description: string;
  }) {
    const amount = parseFloat(values.amount);
    if (!amount || amount <= 0) {
      showToast({ style: Toast.Style.Failure, title: "Enter a valid amount" });
      return;
    }

    try {
      await createExpense({
        amount,
        category: values.category,
        vendor: values.vendor || null,
        projectId: values.projectId ? parseInt(values.projectId) : null,
        description: values.description || null,
      });
      showToast({
        style: Toast.Style.Success,
        title: "Expense saved",
        message: `$${amount.toFixed(2)} — ${values.category}`,
      });
      popToRoot();
    } catch (err) {
      showToast({ style: Toast.Style.Failure, title: "Failed to save expense", message: String(err) });
    }
  }

  return (
    <Form
      isLoading={isLoading}
      actions={
        <ActionPanel>
          <Action.SubmitForm title="Save Expense" onSubmit={handleSubmit} />
        </ActionPanel>
      }
    >
      <Form.TextField id="amount" title="Amount" placeholder="0.00" />
      <Form.Dropdown id="category" title="Category" storeValue>
        {EXPENSE_CATEGORIES.map((c) => (
          <Form.Dropdown.Item key={c} value={c} title={c.charAt(0).toUpperCase() + c.slice(1)} />
        ))}
      </Form.Dropdown>
      <Form.TextField id="vendor" title="Vendor" placeholder="e.g. GitHub, AWS" />
      <Form.Dropdown id="projectId" title="Project">
        <Form.Dropdown.Item value="" title="None" />
        {projects.map((p) => (
          <Form.Dropdown.Item
            key={p.id}
            value={String(p.id)}
            title={p.clientName ? `${p.name} (${p.clientName})` : p.name}
          />
        ))}
      </Form.Dropdown>
      <Form.TextField id="description" title="Note" placeholder="Optional" />
    </Form>
  );
}

import { Action, ActionPanel, Form, showToast, Toast, popToRoot, useNavigation } from "@raycast/api";
import { createClient } from "./api";

interface Props {
  onCreated?: () => void;
}

export default function CreateClient({ onCreated }: Props = {}) {
  const { pop } = useNavigation();

  async function handleSubmit(values: {
    name: string;
    email: string;
    phone: string;
    paymentTermsDays: string;
  }) {
    if (!values.name.trim()) {
      showToast({ style: Toast.Style.Failure, title: "Name is required" });
      return;
    }

    try {
      const client = await createClient({
        name: values.name.trim(),
        email: values.email || null,
        phone: values.phone || null,
        paymentTermsDays: values.paymentTermsDays ? parseInt(values.paymentTermsDays) : null,
      });
      showToast({ style: Toast.Style.Success, title: "Client created", message: client.name });
      if (onCreated) {
        onCreated();
        pop();
      } else {
        popToRoot();
      }
    } catch (err) {
      showToast({ style: Toast.Style.Failure, title: "Failed to create client", message: String(err) });
    }
  }

  return (
    <Form
      actions={
        <ActionPanel>
          <Action.SubmitForm title="Create Client" onSubmit={handleSubmit} />
        </ActionPanel>
      }
    >
      <Form.TextField id="name" title="Name" placeholder="Company or person name" />
      <Form.TextField id="email" title="Email" placeholder="Optional" />
      <Form.TextField id="phone" title="Phone" placeholder="Optional" />
      <Form.TextField id="paymentTermsDays" title="Payment Terms (days)" placeholder="30" />
    </Form>
  );
}

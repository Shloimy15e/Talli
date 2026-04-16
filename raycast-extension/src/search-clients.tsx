import { Action, ActionPanel, Icon, List, showToast, Toast, useNavigation } from "@raycast/api";
import { useEffect, useState } from "react";
import { fetchClients, ClientResponse } from "./api";
import CreateClient from "./create-client";

export default function Command() {
  const [clients, setClients] = useState<ClientResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchText, setSearchText] = useState("");
  const { push } = useNavigation();

  useEffect(() => {
    async function load() {
      setIsLoading(true);
      try {
        setClients(await fetchClients(searchText || undefined));
      } catch (err) {
        showToast({ style: Toast.Style.Failure, title: "Failed to load clients", message: String(err) });
      }
      setIsLoading(false);
    }
    load();
  }, [searchText]);

  return (
    <List
      isLoading={isLoading}
      searchText={searchText}
      onSearchTextChange={setSearchText}
      searchBarPlaceholder="Search clients..."
      throttle
    >
      {clients.map((c) => (
        <List.Item
          key={c.id}
          icon={Icon.Person}
          title={c.name}
          subtitle={c.email || undefined}
          accessories={[
            c.phone ? { text: c.phone, icon: Icon.Phone } : {},
            { text: `Net ${c.paymentTermsDays}`, icon: Icon.Calendar },
          ]}
          actions={
            <ActionPanel>
              <Action.CopyToClipboard title="Copy Name" content={c.name} />
              {c.email && <Action.CopyToClipboard title="Copy Email" content={c.email} />}
              {c.phone && <Action.CopyToClipboard title="Copy Phone" content={c.phone} />}
            </ActionPanel>
          }
        />
      ))}
      <List.EmptyView
        icon={Icon.Person}
        title="No clients found"
        description={searchText ? "Try a different search" : "Create your first client"}
        actions={
          <ActionPanel>
            <Action
              title="Create Client"
              icon={Icon.Plus}
              onAction={() => push(<CreateClient onCreated={() => setSearchText("")} />)}
            />
          </ActionPanel>
        }
      />
    </List>
  );
}

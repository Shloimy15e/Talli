/// <reference types="@raycast/api">

/* 🚧 🚧 🚧
 * This file is auto-generated from the extension's manifest.
 * Do not modify manually. Instead, update the `package.json` file.
 * 🚧 🚧 🚧 */

/* eslint-disable @typescript-eslint/ban-types */

type ExtensionPreferences = {
  /** Server URL - Your Talli instance URL */
  "serverUrl": string,
  /** API Token - Personal access token from your Talli profile page */
  "apiToken": string
}

/** Preferences accessible in all the extension's commands */
declare type Preferences = ExtensionPreferences

declare namespace Preferences {
  /** Preferences accessible in the `start-timer` command */
  export type StartTimer = ExtensionPreferences & {}
  /** Preferences accessible in the `stop-timer` command */
  export type StopTimer = ExtensionPreferences & {}
  /** Preferences accessible in the `log-expense` command */
  export type LogExpense = ExtensionPreferences & {}
  /** Preferences accessible in the `timer-status` command */
  export type TimerStatus = ExtensionPreferences & {}
  /** Preferences accessible in the `search-clients` command */
  export type SearchClients = ExtensionPreferences & {}
  /** Preferences accessible in the `create-client` command */
  export type CreateClient = ExtensionPreferences & {}
}

declare namespace Arguments {
  /** Arguments passed to the `start-timer` command */
  export type StartTimer = {}
  /** Arguments passed to the `stop-timer` command */
  export type StopTimer = {}
  /** Arguments passed to the `log-expense` command */
  export type LogExpense = {}
  /** Arguments passed to the `timer-status` command */
  export type TimerStatus = {}
  /** Arguments passed to the `search-clients` command */
  export type SearchClients = {}
  /** Arguments passed to the `create-client` command */
  export type CreateClient = {}
}


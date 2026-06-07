# Talli Website Schema Guide

This guide is for AI agents building future client websites that should be editable through Talli.

Important: for future websites, do not plan on a custom Java adapter per website.

Talli and the future websites are controlled together, so the best architecture is one Talli editor schema convention that every future website repo includes. Talli has one Java adapter for that convention; future websites should not need their own Java classes.

Northlight-style adapters are useful for legacy or transitional websites. They should not be the default for new websites.

## The Convention

Future website repos should include a Talli schema and semantic content files. The names below are examples, not requirements:

```text
talli/
  editor.schema.json
content/
  home.json
  about.json
  services.json
  projects.json
  contact.json
public/
  images/
```

The public website renders from `content/*.json`.

Talli reads `talli/editor.schema.json` and the referenced content files.

This keeps future sites on one convention instead of one adapter per app.

## Dynamic Pages And Content

Talli does not require a fixed set of pages, file names, or content fields.

The schema decides:

- which content files exist
- which editor sections appear
- which fields are editable
- where each field writes in JSON
- which arrays are repeatable

These are all valid content files if the schema references them:

```text
content/landing.json
content/workshops.json
content/team/advisors.json
content/legal/privacy.json
content/global/navigation.json
```

Editor sections do not have to match public website routes one-to-one. A section may represent a page, a reusable website area, a collection, or global settings.

Examples:

- `Landing`
- `Advisors`
- `Case studies`
- `Privacy notice`
- `Header and footer`

The important constraint is not page shape. The important constraint is editor simplicity: describe content with Talli's small set of field and block types, and keep technical website decisions out of the client's view.

## Core Principle

The schema should describe what the client edits, not how the website is visually built.

Good visible editor fields:

- Main headline
- Intro text
- Service title
- Project location
- Contact email
- Project images

Bad visible editor fields:

- Component name
- CSS class
- Sort order
- Layout type
- Color token
- Manual number
- Slug
- File path

The website code owns presentation. Talli owns content editing.

## Canonical Files

### `talli/editor.schema.json`

This is the only file Talli needs to understand the editor.

It defines:

- schema version
- content files
- editor sections
- field labels
- JSON pointers into content files
- repeatable arrays
- validation rules
- image upload behavior
- computed values

### `content/*.json`

These are semantic content files used by the website itself.

They should be clean enough that a human or AI agent can understand them without knowing Talli internals.

## Minimal Talli Schema Example

```json
{
  "version": "talli-editor/v1",
  "contentFiles": [
    "content/home.json",
    "content/services.json",
    "content/contact.json"
  ],
  "sections": [
    {
      "id": "home",
      "label": "Home page",
      "title": "First impression",
      "icon": "home",
      "blocks": [
        {
          "type": "fields",
          "layout": "two-column",
          "fields": [
            {
              "id": "homeHeroHeadline",
              "type": "text",
              "label": "Main headline",
              "placeholder": "The headline visitors see first",
              "source": {
                "file": "content/home.json",
                "path": "/hero/headline"
              },
              "required": "Home page main headline is empty."
            },
            {
              "id": "homeHeroText",
              "type": "textarea",
              "label": "Intro text",
              "rows": 4,
              "source": {
                "file": "content/home.json",
                "path": "/hero/text"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

## Repeat Blocks

Use repeat blocks for arrays the client may add, remove, or reorder.

Example content:

```json
{
  "approach": {
    "title": "Our approach",
    "pillars": [
      {
        "number": "1",
        "title": "Understanding",
        "description": "Client goals and priorities come first."
      }
    ]
  }
}
```

Example schema:

```json
{
  "type": "repeat",
  "id": "homePillars",
  "title": "Approach pillars",
  "help": "Short cards that explain the way the business works.",
  "itemLabel": "Pillar",
  "addLabel": "Add pillar",
  "source": {
    "file": "content/home.json",
    "path": "/approach/pillars"
  },
  "computed": [
    {
      "path": "/number",
      "value": "index",
      "start": 1,
      "format": "plain"
    }
  ],
  "fields": [
    {
      "id": "title",
      "type": "text",
      "label": "Title",
      "path": "/title",
      "required": "Pillar {number} needs a title."
    },
    {
      "id": "description",
      "type": "textarea",
      "label": "Description",
      "rows": 3,
      "path": "/description"
    }
  ]
}
```

Notice that `number` exists in content because the public website may use it, but it is not editable. Talli generates it from row order.

## Field Types

The Talli schema should stay small. These are the preferred field types:

```text
text
email
textarea
image
imageList
```

These block types are preferred:

```text
fields
repeat
```

Avoid adding field types unless the editor truly needs them. Every new field type increases the chance the editor starts feeling like Wix.

## Field Object Reference

### Text

```json
{
  "id": "contactHeading",
  "type": "text",
  "label": "Contact heading",
  "placeholder": "Contact us",
  "source": {
    "file": "content/contact.json",
    "path": "/heading"
  }
}
```

### Email

```json
{
  "id": "contactEmail",
  "type": "email",
  "label": "Email address",
  "placeholder": "info@example.com",
  "source": {
    "file": "content/contact.json",
    "path": "/footer/email"
  },
  "required": "Contact email is empty.",
  "invalid": "Contact email is not a valid email address."
}
```

### Textarea

```json
{
  "id": "aboutIntroParagraphs",
  "type": "textarea",
  "label": "Intro paragraphs",
  "rows": 5,
  "source": {
    "file": "content/about.json",
    "path": "/intro/paragraphs"
  },
  "transform": "paragraphs"
}
```

`transform: "paragraphs"` means Talli may show the array as one textarea and split it back into an array on publish.

### Image

```json
{
  "id": "founderImage",
  "type": "image",
  "label": "Founder photo",
  "source": {
    "file": "content/about.json",
    "path": "/founder/image/src"
  },
  "upload": {
    "directory": "public/images/cms/{projectId}",
    "filename": "founder",
    "publicPath": "/images/cms/{projectId}/{filename}.{extension}",
    "accept": ["image/jpeg", "image/png", "image/webp"]
  },
  "help": "Recommended: clear JPG, PNG, or WEBP image."
}
```

### Image List

Use image lists inside repeat items.

```json
{
  "id": "images",
  "type": "imageList",
  "label": "Images",
  "path": "/images",
  "item": {
    "srcPath": "/src",
    "altPath": "/alt"
  },
  "upload": {
    "directory": "public/images/cms/{projectId}",
    "filename": "project-{rowNumber}-{imageNumber}",
    "publicPath": "/images/cms/{projectId}/{filename}.{extension}",
    "accept": ["image/jpeg", "image/png", "image/webp"]
  }
}
```

## JSON Pointer Rules

Use JSON Pointer paths.

Examples:

```text
/hero/headline
/footer/email
/approach/pillars
/title
/images
```

Top-level fields use `source.file` and `source.path`.

Repeat item fields use `path` relative to the array item.

## Content Schema Rules

Use semantic content files:

```json
{
  "hero": {
    "headline": "",
    "subheadline": "",
    "text": ""
  }
}
```

Avoid implementation content files:

```json
{
  "section3LeftHeading": "",
  "orangeCardText": "",
  "componentProps": {}
}
```

## Repeatable Content Rules

Prefer arrays of objects:

```json
{
  "services": [
    {
      "title": "",
      "description": "",
      "image": {
        "src": "",
        "alt": ""
      }
    }
  ]
}
```

Avoid arrays of strings unless the list is truly simple and stable:

```json
{
  "categories": [
    "Office",
    "Retail"
  ]
}
```

If there is any chance the item will later need description, image, or metadata, use objects from the beginning.

## Computed Values

Use computed values for anything technical or display-only.

Examples:

- numbers
- slugs
- image filenames
- default labels
- derived copyright year

Example:

```json
{
  "computed": [
    {
      "path": "/number",
      "value": "index",
      "start": 1,
      "format": "2-digit"
    },
    {
      "path": "/slug",
      "value": "slug",
      "from": "/title"
    }
  ]
}
```

The client should not edit these.

## Validation Rules

Use validation only when the live website would be broken or a business-critical action would fail.

Good required fields:

- Home page main headline
- Contact email
- Service title when a service row has content
- Project location when a project row has content

Usually optional:

- subtitles
- secondary paragraphs
- images
- alt text
- footer copyright

Validation messages should be plain:

```text
Contact email is empty.
Service 2 needs a title.
Project 3 needs a location.
```

Avoid technical messages:

```text
services[1].title cannot be null.
```

## Recommended Website Content Shape

### `content/home.json`

```json
{
  "hero": {
    "headline": "",
    "subheadline": "",
    "text": ""
  },
  "approach": {
    "title": "",
    "intro": "",
    "pillars": [
      {
        "number": "1",
        "title": "",
        "description": ""
      }
    ]
  },
  "highlights": {
    "title": "",
    "paragraphs": [],
    "image": {
      "src": "",
      "alt": ""
    }
  }
}
```

### `content/services.json`

```json
{
  "intro": {
    "title": "",
    "subtitle": "",
    "paragraph": ""
  },
  "services": [
    {
      "title": "",
      "description": "",
      "image": {
        "src": "",
        "alt": ""
      }
    }
  ]
}
```

### `content/projects.json`

```json
{
  "heading": "",
  "subheading": "",
  "projects": [
    {
      "location": "",
      "summary": "",
      "stats": "",
      "images": [
        {
          "src": "",
          "alt": ""
        }
      ]
    }
  ]
}
```

### `content/contact.json`

```json
{
  "heading": "",
  "text": "",
  "subtext": "",
  "footer": {
    "address": "",
    "email": "",
    "copyright": ""
  }
}
```

## How Talli Uses The Schema

Talli's schema adapter can:

- read `talli/editor.schema.json`
- validate schema version
- read declared `contentFiles`
- map schema sections into `WebsiteEditorForm`
- resolve JSON Pointer paths
- render repeat blocks
- apply text/email/textarea/image/imageList updates
- apply computed values
- preserve unknown keys
- write changed JSON files
- write uploaded images to deterministic paths

This is the default path for future apps.

Adapters remain useful only when:

- the website already exists with a non-Talli schema
- a legacy schema is too awkward to migrate immediately
- the public website uses a structure Talli should not ask future sites to copy

## Agent Checklist

Before building a future website:

- Create `talli/editor.schema.json`.
- Create semantic `content/*.json` files.
- Use the Talli schema instead of planning a custom Java adapter.
- Keep visible fields client-facing.
- Keep arrays ordered by display order.
- Use computed values for numbers, slugs, and image filenames.
- Use image objects with `src` and optional `alt`.
- Avoid layout/style/component controls.

When building the website:

- Render from `content/*.json`.
- Do not hardcode editable copy in components.
- Keep optional fields graceful when empty.
- Generate presentation details in website code.

When checking the schema:

- Can Talli render it with the schema-based website adapter?
- Can a nontechnical client understand every visible field?
- Does each repeat item expose a clear visible `title`, `name`, or `location` field so Talli can summarize rows?
- Are generated fields hidden?
- Are validation messages plain?

## Final Test

If a future website needs a new Java adapter, ask why.

For new websites controlled by Talli, the correct answer should almost always be:

Use the Talli website schema instead.

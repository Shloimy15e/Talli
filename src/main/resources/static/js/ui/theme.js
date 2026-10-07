/* Keep legacy Tailwind screens on the same semantic palette as Mail. */
(() => {
  if (!window.tailwind) return;
  const color = (role) => ({ opacityValue }) => opacityValue === undefined
    ? `var(--ui-${role})`
    : `color-mix(in srgb, var(--ui-${role}) calc(${opacityValue} * 100%), transparent)`;
  tailwind.config = {
    theme: {
      extend: {
        colors: {
          ui: Object.fromEntries([
            'ink', 'muted', 'line', 'border-strong', 'surface', 'canvas', 'subtle',
            'accent', 'accent-hover', 'accent-foreground', 'accent-ink', 'selected',
            'focus', 'hover', 'active', 'rail', 'rail-ink', 'rail-accent',
          ].map((role) => [role, color(role)])),
          slate: {
            50: color('subtle'), 100: color('canvas'), 200: color('line'),
            300: color('border-strong'), 400: color('muted'), 500: color('muted'),
            600: color('muted'), 700: color('ink'), 800: color('ink'), 900: color('ink'),
          },
        },
      },
    },
  };
})();

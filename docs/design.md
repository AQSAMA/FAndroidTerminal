# Foundation visual direction

June is the primary component reference: connected surfaces, rounded grouped controls,
clear hierarchy, generous touch targets and action-driven motion. Adapt its component code
rather than copying journal-specific navigation or domain objects.

The terminal is the dominant work surface. App chrome follows the system light/dark theme;
the initial terminal palette remains a deliberately stable dark canvas to preserve ANSI
contrast. Core colors: terminal #141218, text #E6E1E5, primary #D0BCFF, on-primary #381E72,
control surface #2B2930. App typography follows Material; terminal uses Android monospace
fallback. UI direction follows locale; terminal grid remains LTR.

Header identifies Terminal and Android shell, tabs expose explicit close actions, and a
scrollable floating key group sits above the IME. No dashboard full of unavailable features,
background effects, fake telemetry or motion inside the terminal cell grid. Keep actions
reachable one-handed and test narrow layouts with the keyboard visible.

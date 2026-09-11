# SaoWorld adaptation of zMenu 1.1.1.8

Base: [official main, commit 7de6a902](https://github.com/Maxlego08/zMenu/tree/7de6a902a31eb7bfb96154f210b2aa0e2aae99e5).
Artifact: `zMenu-1.1.1.8-saoworld.1.jar`, identified by a `SaoWorld-Patch` manifest entry. The plugin version remains numeric `1.1.1.8` because the upstream update checker rejects suffixes. This is an independent clone; the existing SaoWorld fork is preserved in `../zMenu`.

## Inventory behavior

- Hidden menus use PacketEvents without moving, serializing or restoring real items. A hidden menu is refused when PacketEvents is unavailable.
- The next menu's packet view is published during preparation after its requirements pass, before opening its container. `WINDOW_ITEMS`, `SET_SLOT` and `SET_PLAYER_INVENTORY` packets use that view immediately, including during transitions between two hidden menus.
- Closing the previous container cannot clear the replacement view. Cancelled openings restore the previous view. Stale button callbacks cannot overwrite a newer menu.
- Ordinary closure removes the view and refreshes the player's current inventory. Armor and the cursor are not masked; the offhand is masked for consistency with upstream packet clearing.
- Hidden-menu inventory clicks cannot move real items; menu button actions still run.
- The `InventoriesPlayer` service interface remains available to Core and Characters. Physical storage is rejected; display restoration only refreshes the current inventory. No recovery rows are loaded, replayed or deleted. Existing database files and rows must be preserved during deployment and rollback.
- The old fork's snapshot reconciliation, forced restores, SQL changes and unrelated skull-cache patches are not carried over. Native upstream features otherwise remain the base.

## Validation

- Compile and packet tests use PacketEvents `2.13.0`, matching the user's updated target.
- Five packet tests cover transitions before post-open, stale closes/updates, cancelled/denied openings, visible menus, slot mapping, initial container contents, incremental slots, offhand, armor and cursor preservation.
- Two service tests cover external refresh, absence of snapshot/storage calls and rejection of physical capture before accessing player items.
- Required full command: `.\gradlew.bat build --console=plain`, with JDK 25 as required by upstream. Main plugin and projection classes target Java 21; upstream includes a separate optional Paper 26 adapter.
- Local source import check: all 30 distinct zMenu imports across 43 SaoWorld projects resolve. This does not replace a server integration test.
- `git diff --check` passes.

## Activation and remaining checks

No server files have been changed, and no restart has been performed. Before activation, back up the deployed JAR and zMenu data, verify the deployed configuration and dependency graph, and use the normal server restart workflow. Upstream now uses `paper-plugin.yml` and a Paper loader.

In game, verify main menu -> attributes -> BACK repeatedly, rapid clicks, ESC, a cancelled opening, transition to a visible inventory, purchases received while a menu is open, translated bottom buttons, disconnect/reconnect and death. The visual absence of flicker is not yet confirmed on a live client. Keep the existing SaoWorld-Packets refresh hook enabled for translated physical items after closure.

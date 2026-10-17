# Block entity renderer budget

NeroSecurity draws only what must move with block entity renderers (BERs): door panels while they
animate, laser beam halves, camera bodies, chest lids, emitter cores, alarm beacons and status LEDs.
Everything else is a static JSON model, and force field blocks have no BER at all (their motion is a
16-frame animated texture). This page holds the budget those renderers must meet and the measurements
taken against it.

The hard ceiling is the per-render-type `BufferBuilder` limit of 16 777 215 vertices per frame. An unculled
renderer that overflows it crashes the client. The budget below sits far beneath that ceiling.

## Budget (release gate)

| Metric | Gate |
|---|---|
| NeroSecurity BER vertices per frame, case (b) | at most 60 000 |
| NeroSecurity BER time per frame, case (b) | at most 1.5 ms |
| Frame time, case (a) compared with case (c) | within 10 % |
| `BufferBuilder` warnings in the log during the run | none |

Computed worst case for case (b): about 50 × 208 (doors) + 100 × 64 (laser nodes) + 10 × 72 (cameras)
+ 10 × 48 (alarms) + 10 × 96 (emitters) + 5 × 112 (chests) + 16 (hub), which comes to roughly 20 000 vertices.

## Per-renderer cost

| Renderer | Draws | Cull box | View distance | Vertices when active | Active when |
|---|---|---|---|---|---|
| Security hub | recording LED, lockdown strip | block | 32 | 16 | always (LEDs) |
| Secure chest | body, lid, plate, LED | block | 48 | 112 | always |
| Security / blast door | 2 or 4 panels, 2 LEDs | the door's 1×2 or 2×2 box | 64 | 112 / 208 (16 when still) | LEDs always; panels only while animating |
| Laser grid node | beam half, trip ring | lens to beam midpoint, +0.3 | 64 | 64 | armed and paired |
| Security camera | body, lens, LED, bloom | block, +0.25 | 48 | 72 | always |
| Alarm | 2 blades, light column | block to +6 Y | 64 | 48 | active |
| Force field emitter | 2 core cubes | block | 48 | 96 | always (still when idle) |

## Rules every renderer follows

- `shouldRender` is `getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)`. It never
  returns a blanket `true`.
- `shouldRenderOffScreen` is never overridden.
- `extractRenderState` reads only the block entity's synced fields. It never queries entities,
  neighbouring block states or saved data.
- A renderer with nothing to show submits nothing and counts the call as culled.
- Motion is driven by client time from a synced anchor, so it needs no per-tick packets. With
  `renderAnimationsEnabled = false`, every device draws a still frame.

## How to measure

`BerStats` counts BERs submitted and culled, vertices, and BER time (extract plus submit) per frame. It
averages the counts over the last 200 frames. The client command `/nsber` prints the short line
`[NeroSecurity] BER n/m v verts t ms` and the full record to paste below; `/nsber reset` starts a new window.
The command root is `nsber`, not `nerosecurity`, because a client command with the same root as a server
command would hide the server's own subcommands. Read the frame time from the vanilla debug screen (F3).

### Reference scene

Build it in a flat creative world. The exact `/setblock` and `/fill` lines are recorded with the first
measurement.

- 50 security doors in a 10×5 wall, all linked; 25 of them carry a hardness module
- 50 laser pairs (100 nodes, 8 blocks apart) in two planes of 25 pairs
- 10 cameras; 10 active alarms; 10 emitters projecting 5×5 walls; 5 secure chests; 1 hub
- Camera 20 blocks back, GUI scale 2, render distance 12, vanilla client, no shaders

### Cases

- **(a) idle**: doors closed, beams armed.
- **(b) lockdown**: lockdown entered, so all 50 doors seal at the same moment (the worst door frame).
- **(c) baseline**: `renderAnimationsEnabled = false`.

## Measurements

Record at least NeoForge 26.2 and Fabric 26.1.2.

| Date | Loader / MC | Machine class | Case | BERs submitted / culled | Vertices / frame (avg, peak) | BER ms / frame (avg, peak) | Frame time |
|---|---|---|---|---|---|---|---|
| _pending_ | NeoForge 26.2 | | (a) | | | | |
| _pending_ | NeoForge 26.2 | | (b) | | | | |
| _pending_ | NeoForge 26.2 | | (c) | | | | |
| _pending_ | Fabric 26.1.2 | | (a) | | | | |
| _pending_ | Fabric 26.1.2 | | (b) | | | | |
| _pending_ | Fabric 26.1.2 | | (c) | | | | |

## Culling check

Paste the output of these two commands, run from the repository root:

```text
grep -rn "shouldRender" common/src/main/java
grep -rn "shouldRenderOffScreen" common/src/main/java
```

The first should list only the inflate-contains lines. The second should print nothing.

Recorded 2026-10-17 (the `-A2` shows each method body; `BerStats.java` only mentions the word in a comment):

```text
$ grep -rn -A2 "shouldRender" common/src/main/java
common/src/main/java/za/co/neroland/nerosecurity/client/render/alarm/AlarmBeaconRenderer.java:56:    public boolean shouldRender(AlarmBlockEntity be, Vec3 cameraPos) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/alarm/AlarmBeaconRenderer.java-57-        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/alarm/AlarmBeaconRenderer.java-58-            return true;
common/src/main/java/za/co/neroland/nerosecurity/client/render/camera/SecurityCameraRenderer.java:65:    public boolean shouldRender(SecurityCameraBlockEntity be, Vec3 cameraPos) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/camera/SecurityCameraRenderer.java-66-        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/camera/SecurityCameraRenderer.java-67-            return true;
common/src/main/java/za/co/neroland/nerosecurity/client/render/chest/SecureChestRenderer.java:70:    public boolean shouldRender(SecureChestBlockEntity be, Vec3 cameraPos) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/chest/SecureChestRenderer.java-71-        boolean visible = getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos);
common/src/main/java/za/co/neroland/nerosecurity/client/render/chest/SecureChestRenderer.java-72-        if (!visible) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/door/SecurityDoorRenderer.java:84:    public boolean shouldRender(SecurityDoorBlockEntity be, Vec3 cameraPos) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/door/SecurityDoorRenderer.java-85-        boolean visible = getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos);
common/src/main/java/za/co/neroland/nerosecurity/client/render/door/SecurityDoorRenderer.java-86-        if (!visible) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/field/ForceFieldEmitterRenderer.java:81:    public boolean shouldRender(ForceFieldEmitterBlockEntity be, Vec3 cameraPos) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/field/ForceFieldEmitterRenderer.java-82-        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/field/ForceFieldEmitterRenderer.java-83-            return true;
common/src/main/java/za/co/neroland/nerosecurity/client/render/hub/SecurityHubRenderer.java:57:    public boolean shouldRender(SecurityHubBlockEntity be, Vec3 cameraPos) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/hub/SecurityHubRenderer.java-58-        boolean visible = getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos);
common/src/main/java/za/co/neroland/nerosecurity/client/render/hub/SecurityHubRenderer.java-59-        if (!visible) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/laser/LaserBeamRenderer.java:69:    public boolean shouldRender(LaserGridNodeBlockEntity be, Vec3 cameraPos) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/laser/LaserBeamRenderer.java-70-        if (getRenderBoundingBox(be).inflate(getViewDistance()).contains(cameraPos)) {
common/src/main/java/za/co/neroland/nerosecurity/client/render/laser/LaserBeamRenderer.java-71-            return true;

$ grep -rn "shouldRenderOffScreen" common/src/main/java
(no output)
```

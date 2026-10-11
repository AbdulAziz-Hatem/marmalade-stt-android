/*
 * Generates the shipping launcher icon from the same geometry the icon lab
 * uses, so the art can never drift from what was signed off there.
 *
 *   node docs/design/build-launcher-icon.mjs
 *
 * Writes:
 *   app/src/main/res/drawable/ic_launcher_foreground.xml   (mascot + mic)
 *   app/src/main/res/drawable/ic_launcher_background.xml    (flat field)
 *   app/src/main/res/drawable/ic_launcher_monochrome.xml    (themed-icon layer)
 *   docs/design/assets/launcher-icon-512.svg                (flattened, for PNG)
 *   docs/design/assets/launcher-icon-monochrome.svg         (preview of the above)
 *
 * Adapted from marmalade-tts-android's build script: same jar, same finish,
 * the sound waves replaced by a microphone. The RECIPE below is Max's M1 pick
 * from docs/design/icon-lab.html (2026-09-27). Change it here and re-run to
 * revise the icon; keep the lab's glyphMic() in step with micGeometry().
 */
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createContext, runInContext } from "node:vm";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, "..", "..");

// icon-art.js is a classic browser script (the lab loads it via <script src>).
// Its top-level `const`s live in the script's lexical scope, not on the
// sandbox, so read them back as the completion value of a trailing expression.
const { JAR, JAR_IDX, JAR_SILHOUETTE } = runInContext(
  readFileSync(join(HERE, "icon-art.js"), "utf8") + "\n({JAR, JAR_IDX, JAR_SILHOUETTE});",
  createContext({}), { filename: "icon-art.js" });

/* ------------------------------------------------------------------ recipe */
const RECIPE = {
  // layout (lab M1, tuned by Max)
  js: 0.76, dx: 0.5, gap: 6.5, micY: 0, anchor: 60.2,
  // mic: classic capsule, base bar
  ms: 1.22, len: 9.5, base: true, sw: 2.2,
  // background: flat fill sampled from the TTS amber ramp ("Burnt amber")
  depth: 0.9,
  ramp: [[0, "#FDBA74"], [0.38, "#F97316"], [1, "#C2410C"]],
  // mascot finish: identical to the shipped TTS icon
  outline: 2.4, outlineCol: "#FFF8EE",
  shadow: 0.18, shDist: 1.6,
  gloss: 0.75,
};

/* ------------------------------------------------------------- colour maths */
const hex2rgb = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const rgb2hex = c => "#" + c.map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0")).join("");
const mix = (a, b, t) => rgb2hex(hex2rgb(a).map((v, i) => v + (hex2rgb(b)[i] - v) * t));
function ramp(t) {
  const R = RECIPE.ramp;
  for (let i = 0; i < R.length - 1; i++) {
    const [p0, c0] = R[i], [p1, c1] = R[i + 1];
    if (t <= p1) return mix(c0, c1, (t - p0) / (p1 - p0));
  }
  return R[R.length - 1][1];
}
const BG = ramp(RECIPE.depth);
const CREAM = "#FFF8EE", INK = "#3D2B1F", JAR_BASE = "#F5A623";

/* ----------------------------------------------------------------- geometry */
const P = i => JAR[i][0];
const f2 = n => +n.toFixed(2);

// Classic mic in local coords (origin = centre of the capsule's bottom cap).
// Every gap equals the stroke width: the cradle is concentric with the bottom
// cap, and the base bar is as wide as the capsule.
function micGeometry(len, base, sw) {
  const W = 4.4, R = W + sw * 1.5, stemEnd = R + 3.3, h = sw / 2;
  const capsule = `M${-W},${-len} L${-W},0 A${W},${W} 0 0 0 ${W},0 L${W},${-len} A${W},${W} 0 0 0 ${-W},${-len} Z`;
  const strokes = [
    `M${-R},-1 L${-R},0 A${R},${R} 0 0 0 ${R},0 L${R},-1`,
    `M0,${R} L0,${stemEnd}`,
  ];
  if (base) strokes.push(`M${-W},${stemEnd} L${W},${stemEnd}`);
  return { capsule, strokes, box: [-R - h, R + h, -len - W, stemEnd + h] };
}
const MIC = micGeometry(RECIPE.len, RECIPE.base, RECIPE.sw);

const { js, dx, gap, ms } = RECIPE;
const [bx0, bx1, by0, by1] = MIC.box;
const jarW = 52 * js, micW = (bx1 - bx0) * ms;
const left = 54 - (jarW + gap + micW) / 2 + dx;
const JX = left + jarW / 2, JY = 54;
const MX = left + jarW + gap - bx0 * ms;
const MY = JY + (RECIPE.anchor - 60) * js - ((by0 + by1) / 2) * ms + RECIPE.micY;

// Body path bbox — turns the SVG objectBoundingBox gloss gradient into the
// viewport coordinates VectorDrawable wants.
const BODY_BB = { x1: 28.0, y1: 35.3, x2: 79.9, y2: 97.6 };
const bbX = f => BODY_BB.x1 + f * (BODY_BB.x2 - BODY_BB.x1);
const bbY = f => BODY_BB.y1 + f * (BODY_BB.y2 - BODY_BB.y1);
const GLOSS_LITE = mix(JAR_BASE, "#FFFFFF", 0.30 * RECIPE.gloss);
const GLOSS_DARK = mix(JAR_BASE, "#B45309", 0.45 * RECIPE.gloss);

const EYE_C = [[44.6, 60.2], [63.3, 60.2]];
const GLOSS_C = [[46.5, 58.2], [65.2, 58.2]];
const ell = (cx, cy, rx, ry) =>
  `M${(cx - rx).toFixed(2)},${cy} a${rx},${ry} 0 1,0 ${(2 * rx).toFixed(2)},0 a${rx},${ry} 0 1,0 ${(-2 * rx).toFixed(2)},0 Z`;

/* ------------------------------------------------------- VectorDrawable out */
const A = (n, v) => v === undefined || v === null ? "" : ` android:${n}="${v}"`;
function vpath({ d, fill, fillAlpha, stroke, strokeWidth, strokeAlpha, cap, join }, indent = "    ") {
  return `${indent}<path${A("pathData", d)}${A("fillColor", fill ?? "#00000000")}` +
    `${A("fillAlpha", fillAlpha)}${A("strokeColor", stroke)}${A("strokeWidth", strokeWidth)}` +
    `${A("strokeAlpha", strokeAlpha)}${A("strokeLineCap", cap)}${A("strokeLineJoin", join)}/>`;
}
const stopsXml = (stops, indent) =>
  stops.map(s => `${indent}  <item android:offset="${s.off}" android:color="${s.col}"/>`).join("\n");
const jarGroup = (body, indent = "  ") =>
  `${indent}<group\n${indent}    android:pivotX="54"\n${indent}    android:pivotY="60"\n` +
  `${indent}    android:scaleX="${js}"\n${indent}    android:scaleY="${js}"\n` +
  `${indent}    android:translateX="${(JX - 54).toFixed(2)}"\n${indent}    android:translateY="${(JY - 60).toFixed(2)}">\n` +
  `${body}\n${indent}</group>`;
// The mic group maps local p -> p*ms + (MX, MY), the lab's translate() scale().
const micGroup = (body, indent = "  ", ox = 0, oy = 0) =>
  `${indent}<group android:translateX="${(MX + ox).toFixed(2)}" android:translateY="${(MY + oy).toFixed(2)}"` +
  ` android:scaleX="${ms}" android:scaleY="${ms}">\n${body}\n${indent}</group>`;
const micVPaths = (col, alpha, indent) => [
  vpath({ d: MIC.capsule, fill: col, fillAlpha: alpha }, indent),
  ...MIC.strokes.map(d => vpath({ d, stroke: col, strokeWidth: RECIPE.sw, strokeAlpha: alpha, cap: "round" }, indent)),
].join("\n");

function mascotVPaths(indent = "    ") {
  const inner = indent + "  ";
  const out = [];
  // Drop shadow — two offset silhouette passes (VectorDrawable has no blur).
  for (const [k, a] of [[1, 0.55], [0.5, 0.45]]) {
    out.push(`${indent}<group android:translateX="${(RECIPE.shDist * k * 0.75).toFixed(2)}" android:translateY="${(RECIPE.shDist * k).toFixed(2)}">`);
    for (const d of JAR_SILHOUETTE) out.push(vpath({ d, fill: INK, fillAlpha: f2(RECIPE.shadow * a) }, inner));
    out.push(`${indent}</group>`);
  }
  // Sticker outline — silhouette filled and stroked so the pieces union cleanly.
  for (const d of JAR_SILHOUETTE)
    out.push(vpath({ d, fill: RECIPE.outlineCol, stroke: RECIPE.outlineCol, strokeWidth: f2(RECIPE.outline * 2), join: "round" }, indent));
  out.push(`${indent}<path${A("pathData", P(JAR_IDX.body))}>\n` +
    `${indent}  <aapt:attr name="android:fillColor">\n` +
    `${indent}    <gradient android:type="linear"` +
    ` android:startX="${bbX(0.1).toFixed(2)}" android:startY="${bbY(0).toFixed(2)}"` +
    ` android:endX="${bbX(0.85).toFixed(2)}" android:endY="${bbY(1).toFixed(2)}">\n` +
    stopsXml([{ off: 0, col: GLOSS_LITE }, { off: 0.45, col: JAR_BASE }, { off: 1, col: GLOSS_DARK }], indent + "    ") +
    `\n${indent}    </gradient>\n${indent}  </aapt:attr>\n${indent}</path>`);
  out.push(vpath({ d: P(JAR_IDX.marmalade), fill: "#D4831E", fillAlpha: 0.5 }, indent));
  out.push(vpath({ d: P(JAR_IDX.shine), fill: "#FFFFFF", fillAlpha: 0.15 }, indent));
  out.push(vpath({ d: P(JAR_IDX.neck), fill: "#E8D5B0" }, indent));
  out.push(vpath({ d: P(JAR_IDX.lid), fill: "#FFF8EE", stroke: "#D4C4A0", strokeWidth: 0.4 }, indent));
  out.push(vpath({ d: P(JAR_IDX.ridge), fill: "#FFF5E6", stroke: "#D4C4A0", strokeWidth: 0.3 }, indent));
  for (const i of JAR_IDX.lidLines) out.push(vpath({ d: P(i), stroke: "#E0D0B0", strokeWidth: 0.2, strokeAlpha: 0.5 }, indent));
  out.push(vpath({ d: P(JAR_IDX.label), fill: "#FFF8EE", stroke: "#D4B896", strokeWidth: 0.3 }, indent));
  for (const i of JAR_IDX.labelLines) out.push(vpath({ d: P(i), stroke: "#E8C9A0", strokeWidth: 0.2 }, indent));
  out.push(vpath({ d: P(JAR_IDX.slice), fill: JAR_BASE, stroke: "#D4831E", strokeWidth: 0.3 }, indent));
  for (const i of JAR_IDX.sliceLines) out.push(vpath({ d: P(i), stroke: "#FFF5E6", strokeWidth: 0.2, strokeAlpha: 0.6 }, indent));
  for (const [cx, cy] of EYE_C) out.push(vpath({ d: ell(cx, cy, 4.6, 5.4), fill: INK }, indent));
  for (const [cx, cy] of GLOSS_C) out.push(vpath({ d: ell(cx, cy, 1.9, 1.9), fill: "#FFFFFF" }, indent));
  out.push(vpath({ d: "M48.5,69.6 Q54.0,72.0 59.5,69.6", stroke: INK, strokeWidth: 0.5, cap: "round" }, indent));
  for (const [cx, cy] of [[40.0, 63.5], [68.0, 63.5]])
    out.push(vpath({ d: ell(cx, cy, 3.5, 1.8), fill: "#FF6B6B", fillAlpha: 0.08 }, indent));
  return out.join("\n");
}

const HEADER = "GENERATED by docs/design/build-launcher-icon.mjs — do not hand-edit.";
const VD_OPEN = (aapt) => `<vector xmlns:android="http://schemas.android.com/apk/res/android"${aapt ? `
    xmlns:aapt="http://schemas.android.com/aapt"` : ""}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">`;

/* ---------------------------------------------------------------- FOREGROUND */
const foreground = `<?xml version="1.0" encoding="utf-8"?>
<!--
  ${HEADER}
  Marmalade STT icon (Max, 2026-09-27, icon-lab M1): the TTS jar with the
  same sticker outline, gloss and drop shadow, and a cream microphone where
  the TTS sound waves sit. Pairs with @drawable/ic_launcher_background.
-->
${VD_OPEN(true)}

${jarGroup(mascotVPaths())}

  <!-- Mic: soft shadow, then the cream mic -->
${micGroup(micVPaths(INK, 0.3, "    "), "  ", 0.9, 1.1)}
${micGroup(micVPaths(CREAM, undefined, "    "))}
</vector>
`;

/* ---------------------------------------------------------------- BACKGROUND */
const background = `<?xml version="1.0" encoding="utf-8"?>
<!--
  ${HEADER}
  Adaptive-icon background: flat ${BG}, the TTS amber ramp sampled at depth
  ${RECIPE.depth} (deeper than TTS's ${ramp(0.34)} so the two apps separate by colour).
-->
${VD_OPEN(false)}
  <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z" android:fillColor="${BG}"/>
</vector>
`;

/* ---------------------------------------------------------------- MONOCHROME
 * Themed-icon layer: the system tints the whole drawable one colour, so it is
 * a pure silhouette: the jar's outer shapes unioned, plus the mic. No face —
 * knocked-out eyes and mouth read as creepy at this size (Max, 2026-09-27).
 */
const monoSilhouette = JAR_SILHOUETTE.join(" ");
const monochrome = `<?xml version="1.0" encoding="utf-8"?>
<!--
  ${HEADER}
  Themed-icon (monochrome) layer for Android 13+, used only when the user
  turns themed icons on. Single-colour silhouette of the jar and the mic,
  deliberately without the face.
-->
${VD_OPEN(false)}

${jarGroup(`    <path android:pathData="${monoSilhouette}" android:fillColor="#000000"/>`)}

${micGroup(micVPaths("#000000", undefined, "    "))}
</vector>
`;

/* ------------------------------------------------------------ flattened SVGs */
const micT = (ox = 0, oy = 0) => `translate(${(MX + ox).toFixed(2)},${(MY + oy).toFixed(2)}) scale(${ms})`;
const micSvg = (col, op) =>
  `<path d="${MIC.capsule}" fill="${col}"${op ? ` opacity="${op}"` : ""}/>` +
  MIC.strokes.map(d => `<path d="${d}" fill="none" stroke="${col}" stroke-width="${RECIPE.sw}" stroke-linecap="round"${op ? ` opacity="${op}"` : ""}/>`).join("");
const jarT = `translate(${JX.toFixed(2)},${JY}) scale(${js}) translate(-54,-60)`;
const sil = JAR_SILHOUETTE;
const jarSvg = [
  ...[[1, 0.55], [0.5, 0.45]].map(([k, al]) =>
    `<g transform="translate(${(RECIPE.shDist * k * 0.75).toFixed(2)},${(RECIPE.shDist * k).toFixed(2)})" opacity="${(RECIPE.shadow * al).toFixed(3)}">` +
    sil.map(d => `<path d="${d}" fill="${INK}"/>`).join("") + `</g>`),
  ...sil.map(d => `<path d="${d}" fill="${RECIPE.outlineCol}" stroke="${RECIPE.outlineCol}" stroke-width="${(RECIPE.outline * 2).toFixed(2)}" stroke-linejoin="round"/>`),
  `<path d="${P(JAR_IDX.body)}" fill="url(#jb)"/>`,
  `<path d="${P(JAR_IDX.marmalade)}" fill="#D4831E" opacity="0.5"/>`,
  `<path d="${P(JAR_IDX.shine)}" fill="#FFFFFF" opacity="0.15"/>`,
  `<path d="${P(JAR_IDX.neck)}" fill="#E8D5B0"/>`,
  `<path d="${P(JAR_IDX.lid)}" fill="#FFF8EE" stroke="#D4C4A0" stroke-width="0.4"/>`,
  `<path d="${P(JAR_IDX.ridge)}" fill="#FFF5E6" stroke="#D4C4A0" stroke-width="0.3"/>`,
  ...JAR_IDX.lidLines.map(i => `<path d="${P(i)}" fill="none" stroke="#E0D0B0" stroke-width="0.2" opacity="0.5"/>`),
  `<path d="${P(JAR_IDX.label)}" fill="#FFF8EE" stroke="#D4B896" stroke-width="0.3"/>`,
  ...JAR_IDX.labelLines.map(i => `<path d="${P(i)}" fill="none" stroke="#E8C9A0" stroke-width="0.2"/>`),
  `<path d="${P(JAR_IDX.slice)}" fill="${JAR_BASE}" stroke="#D4831E" stroke-width="0.3"/>`,
  ...JAR_IDX.sliceLines.map(i => `<path d="${P(i)}" fill="none" stroke="#FFF5E6" stroke-width="0.2" opacity="0.6"/>`),
  ...EYE_C.map(([cx, cy]) => `<ellipse cx="${cx}" cy="${cy}" rx="4.6" ry="5.4" fill="${INK}"/>`),
  ...GLOSS_C.map(([cx, cy]) => `<circle cx="${cx}" cy="${cy}" r="1.9" fill="#FFFFFF"/>`),
  `<path d="M48.5,69.6 Q54.0,72.0 59.5,69.6" fill="none" stroke="${INK}" stroke-width="0.5" stroke-linecap="round"/>`,
  ...[[40.0, 63.5], [68.0, 63.5]].map(([cx, cy]) => `<ellipse cx="${cx}" cy="${cy}" rx="3.5" ry="1.8" fill="#FF6B6B" opacity="0.08"/>`),
].join("");
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
<defs><linearGradient id="jb" x1="0.1" y1="0" x2="0.85" y2="1"><stop offset="0" stop-color="${GLOSS_LITE}"/><stop offset="0.45" stop-color="${JAR_BASE}"/><stop offset="1" stop-color="${GLOSS_DARK}"/></linearGradient></defs>
<rect x="0" y="0" width="108" height="108" fill="${BG}"/>
<g transform="${jarT}">${jarSvg}</g>
<g transform="${micT(0.9, 1.1)}">${micSvg(INK, 0.3)}</g>
<g transform="${micT()}">${micSvg(CREAM)}</g>
</svg>
`;
const monoSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
<rect x="0" y="0" width="108" height="108" fill="#d8d2ca"/>
<g transform="${jarT}"><path d="${monoSilhouette}" fill="#33302c" fill-rule="nonzero"/></g>
<g transform="${micT()}">${micSvg("#33302c")}</g>
</svg>
`;

const res = join(REPO, "app/src/main/res/drawable");
writeFileSync(join(res, "ic_launcher_foreground.xml"), foreground);
writeFileSync(join(res, "ic_launcher_background.xml"), background);
writeFileSync(join(res, "ic_launcher_monochrome.xml"), monochrome);
mkdirSync(join(HERE, "assets"), { recursive: true });
writeFileSync(join(HERE, "assets/launcher-icon-512.svg"), svg);
writeFileSync(join(HERE, "assets/launcher-icon-monochrome.svg"), monoSvg);
console.log("wrote launcher fg/bg/monochrome + both SVGs; background", BG);

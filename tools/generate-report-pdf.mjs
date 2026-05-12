import fs from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const inputPath = path.join(root, "Final_Project_Report.md");
const outputPath = path.join(root, "Final_Project_Report.pdf");

const raw = await fs.readFile(inputPath, "utf8");

function normalizeMarkdown(md) {
  return md
    .replace(/\r/g, "")
    .replace(/^---$/gm, "")
    .replace(/^# (.+)$/gm, "$1")
    .replace(/^## (.+)$/gm, "$1")
    .replace(/^### (.+)$/gm, "$1")
    .replace(/\*\*(.*?)\*\*/g, "$1")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/[ \t]+$/gm, "");
}

function wrapLine(line, width) {
  if (line.length <= width) return [line];
  const words = line.split(" ");
  const out = [];
  let cur = "";
  for (const w of words) {
    const next = cur ? `${cur} ${w}` : w;
    if (next.length <= width) {
      cur = next;
    } else {
      if (cur) out.push(cur);
      cur = w;
    }
  }
  if (cur) out.push(cur);
  return out;
}

function escapePdfText(s) {
  return s.replace(/\\/g, "\\\\").replace(/\(/g, "\\(").replace(/\)/g, "\\)");
}

const text = normalizeMarkdown(raw);
const lines = [];
for (const line of text.split("\n")) {
  if (!line.trim()) {
    lines.push("");
    continue;
  }
  const prefix = line.startsWith("- ") ? "• " : "";
  const body = line.startsWith("- ") ? line.slice(2) : line;
  const wrapped = wrapLine(`${prefix}${body}`, 94);
  lines.push(...wrapped);
}

const pageWidth = 595.28;
const pageHeight = 841.89;
const left = 54;
const top = 790;
const lineHeight = 14;
const maxLinesPerPage = Math.floor((top - 54) / lineHeight);

const pages = [];
for (let i = 0; i < lines.length; i += maxLinesPerPage) {
  pages.push(lines.slice(i, i + maxLinesPerPage));
}

const objects = [];
const offsets = [0];
let pdf = "%PDF-1.4\n";

function addObject(content) {
  objects.push(content);
}

const fontObjId = 3;
const pageStartObjId = 4;
const contentStartObjId = pageStartObjId + pages.length;
const pagesObjId = 2;
const catalogObjId = 1;

for (let p = 0; p < pages.length; p++) {
  const contentLines = pages[p]
    .map((ln, idx) => {
      const y = top - idx * lineHeight;
      return `1 0 0 1 ${left} ${y} Tm (${escapePdfText(ln)}) Tj`;
    })
    .join("\n");
  const stream = `BT\n/F1 11 Tf\n0.15 0.24 0.20 rg\n${contentLines}\nET`;
  addObject(
    `<< /Length ${Buffer.byteLength(stream, "utf8")} >>\nstream\n${stream}\nendstream`
  );
}

for (let p = 0; p < pages.length; p++) {
  const contentObjId = contentStartObjId + p;
  addObject(
    `<< /Type /Page /Parent ${pagesObjId} 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /Font << /F1 ${fontObjId} 0 R >> >> /Contents ${contentObjId} 0 R >>`
  );
}

addObject("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

const kids = Array.from({ length: pages.length }, (_, i) => `${pageStartObjId + i} 0 R`).join(" ");
addObject(`<< /Type /Pages /Kids [ ${kids} ] /Count ${pages.length} >>`);
addObject(`<< /Type /Catalog /Pages ${pagesObjId} 0 R >>`);

const ordered = [
  objects[objects.length - 1], // catalog id 1
  objects[objects.length - 2], // pages id 2
  objects[objects.length - 3], // font id 3
  ...objects.slice(pages.length, pages.length * 2), // page objs
  ...objects.slice(0, pages.length) // content objs
];

for (let i = 0; i < ordered.length; i++) {
  const id = i + 1;
  offsets.push(Buffer.byteLength(pdf, "utf8"));
  pdf += `${id} 0 obj\n${ordered[i]}\nendobj\n`;
}

const xrefOffset = Buffer.byteLength(pdf, "utf8");
pdf += `xref\n0 ${ordered.length + 1}\n`;
pdf += "0000000000 65535 f \n";
for (let i = 1; i <= ordered.length; i++) {
  pdf += `${String(offsets[i]).padStart(10, "0")} 00000 n \n`;
}
pdf += `trailer\n<< /Size ${ordered.length + 1} /Root ${catalogObjId} 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;

await fs.writeFile(outputPath, pdf, "binary");
console.log(`Generated: ${outputPath}`);

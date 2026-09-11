export function normalizePlanContent(content: string): string {
  const normalized = content.replace(/\r\n/g, "\n").trim();
  if (!normalized) throw new Error("plan content is empty");
  return `${normalized}\n`;
}

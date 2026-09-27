export function pageTarget(value, total, size) {
  const page = Number(value), pages = Math.max(1, Math.ceil(total / size))
  return Number.isInteger(page) && page >= 1 && page <= pages ? page : null
}

import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'

const MAX_READ_BYTES = 1024 * 1024 // 1MB
const MAX_WRITE_BYTES = 5 * 1024 * 1024 // 5MB
const MAX_SEARCH_RESULTS = 100

function safePath(p: string): string {
  const resolved = path.resolve(p)
  // Block access to system-sensitive dirs
  const blocked = [path.resolve('/etc'), path.resolve('/System'), path.resolve('/Windows'), path.resolve('C:\\Windows')]
  if (os.platform() === 'win32') {
    blocked.push(path.resolve('C:\\Windows\\System32'))
  }
  for (const b of blocked) {
    if (resolved.startsWith(b + path.sep) || resolved === b) {
      throw new Error(`Access denied: ${p} is a system directory`)
    }
  }
  // Limit to home dir and common user dirs
  const home = os.homedir()
  if (!resolved.startsWith(home) && !resolved.startsWith(os.tmpdir()) && !resolved.startsWith(path.resolve('/'))) {
    // Allow absolute paths that aren't in home but aren't system dirs (e.g., mounted drives on Linux)
    // On Windows, block absolute paths outside user profile
    if (os.platform() === 'win32' && !resolved.startsWith(home)) {
      throw new Error(`Access denied: ${p} is outside user home directory`)
    }
  }
  return resolved
}

export function readFile(filePath: string, maxBytes?: number): string {
  const resolved = safePath(filePath)
  if (!fs.existsSync(resolved)) {
    throw new Error(`File not found: ${filePath}`)
  }
  const stat = fs.statSync(resolved)
  if (stat.isDirectory()) {
    throw new Error(`Path is a directory: ${filePath}`)
  }
  const limit = maxBytes || MAX_READ_BYTES
  if (stat.size > limit) {
    // Read only the first N bytes
    const buf = Buffer.alloc(limit)
    const fd = fs.openSync(resolved, 'r')
    try {
      fs.readSync(fd, buf, 0, limit, 0)
    } finally {
      fs.closeSync(fd)
    }
    const result = buf.toString('utf-8')
    return result + `\n\n[Truncated: file is ${stat.size} bytes, showing first ${limit}]`
  }
  return fs.readFileSync(resolved, 'utf-8')
}

export function writeFile(filePath: string, content: string): string {
  const resolved = safePath(filePath)
  if (Buffer.byteLength(content, 'utf-8') > MAX_WRITE_BYTES) {
    throw new Error(`Content exceeds maximum size of ${MAX_WRITE_BYTES / 1024 / 1024}MB`)
  }
  // Ensure parent dir exists
  const dir = path.dirname(resolved)
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true })
  }
  fs.writeFileSync(resolved, content, 'utf-8')
  return JSON.stringify({ written: true, path: resolved, bytes: Buffer.byteLength(content, 'utf-8') })
}

export function listDir(dirPath: string): string {
  const resolved = safePath(dirPath)
  if (!fs.existsSync(resolved)) {
    throw new Error(`Directory not found: ${dirPath}`)
  }
  const stat = fs.statSync(resolved)
  if (!stat.isDirectory()) {
    throw new Error(`Path is not a directory: ${dirPath}`)
  }
  const entries = fs.readdirSync(resolved, { withFileTypes: true })
  const result = entries.map(e => ({
    name: e.name,
    type: e.isDirectory() ? 'directory' : e.isFile() ? 'file' : 'other',
    size: e.isFile() ? fs.statSync(path.join(resolved, e.name)).size : undefined
  }))
  return JSON.stringify(result)
}

export function searchFiles(dirPath: string, pattern: string): string {
  const resolved = safePath(dirPath)
  if (!fs.existsSync(resolved)) {
    throw new Error(`Directory not found: ${dirPath}`)
  }
  const results: string[] = []
  const regex = new RegExp(pattern, 'i')

  function walk(dir: string, depth: number) {
    if (results.length >= MAX_SEARCH_RESULTS || depth > 10) return
    let entries: fs.Dirent[]
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true })
    } catch {
      return // skip permission-denied dirs
    }
    for (const e of entries) {
      if (results.length >= MAX_SEARCH_RESULTS) return
      const full = path.join(dir, e.name)
      if (regex.test(e.name)) {
        results.push(full)
      }
      if (e.isDirectory()) {
        walk(full, depth + 1)
      }
    }
  }

  walk(resolved, 0)
  return JSON.stringify({ results: results.slice(0, MAX_SEARCH_RESULTS), truncated: results.length >= MAX_SEARCH_RESULTS })
}

export function fileInfo(filePath: string): string {
  const resolved = safePath(filePath)
  if (!fs.existsSync(resolved)) {
    throw new Error(`Path not found: ${filePath}`)
  }
  const stat = fs.statSync(resolved)
  return JSON.stringify({
    name: path.basename(resolved),
    path: resolved,
    size: stat.size,
    isDirectory: stat.isDirectory(),
    isFile: stat.isFile(),
    created: stat.birthtime.toISOString(),
    modified: stat.mtime.toISOString(),
    accessed: stat.atime.toISOString(),
    permissions: stat.mode.toString(8).slice(-3)
  })
}
import { app } from 'electron'
import * as path from 'path'
import * as fs from 'fs'

/**
 * Find the Java executable path.
 * Priority: JAVA_HOME env var > system PATH.
 */
export function findJava(): string {
  const javaHome = process.env.JAVA_HOME
  if (javaHome) {
    const javaExe = process.platform === 'win32' ? 'java.exe' : 'java'
    const javaPath = path.join(javaHome, 'bin', javaExe)
    if (fs.existsSync(javaPath)) {
      return javaPath
    }
  }
  // Fallback: just "java" and rely on system PATH
  return 'java'
}

/**
 * Find the backend jar file.
 * Dev mode: look in ../java-backend/target/ for *-SNAPSHOT.jar
 * Production mode: look in process.resourcesPath
 */
export function findJar(): string {
  const isPackaged = app.isPackaged

  if (isPackaged) {
    const resourcesPath = process.resourcesPath
    const jarPath = path.join(resourcesPath, 'backend', 'claw-backend.jar')
    if (fs.existsSync(jarPath)) return jarPath
    throw new Error(`Backend jar not found at ${jarPath}`)
  }

  // Dev mode: find in maven target/ or gradle build/libs/
  const backendDir = path.join(__dirname, '..', '..', '..', 'java-backend')
  const mavenTarget = path.join(backendDir, 'target')
  const gradleBuild = path.join(backendDir, 'build', 'libs')

  let searchDir: string
  if (fs.existsSync(mavenTarget)) {
    searchDir = mavenTarget
  } else if (fs.existsSync(gradleBuild)) {
    searchDir = gradleBuild
  } else {
    throw new Error(
      `Backend build directory not found. Searched:\n` +
      `  - ${mavenTarget} (Maven)\n` +
      `  - ${gradleBuild} (Gradle)\n` +
      `Run './gradlew bootJar -x test' (Gradle) or 'mvn package -DskipTests' (Maven) first.`
    )
  }

  const files = fs.readdirSync(searchDir)
  const jar = files.find(f => f.endsWith('-SNAPSHOT.jar') && !f.endsWith('-sources.jar'))
  if (!jar) {
    throw new Error(`No SNAPSHOT jar found in ${searchDir}. Found: ${files.join(', ')}`)
  }

  return path.join(searchDir, jar)
}
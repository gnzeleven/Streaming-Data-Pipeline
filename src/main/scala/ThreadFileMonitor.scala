package org.cs441.proj

import java.nio.file.{Files, Path, WatchEvent, WatchKey}
import java.nio.file.StandardWatchEventKinds.{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}
import scala.collection.convert.ImplicitConversions.`iterator asScala`

class ThreadFileMonitor(val root: Path) extends Thread with FileMonitor {
  setDaemon(true)
  setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = onException(e)
  })

  val service = root.getFileSystem.newWatchService()

  override def run() = Iterator.continually(service.take()).foreach(process)

  override def interrupt() = {
    service.close()
    super.interrupt()
  }

  override def start() = {
    if (Files.isDirectory(root)) {
      watch(root, recursive=true)
    } else {
      watch(root.getParent)
    }
    super.start()
  }

  protected[this] def watch(file: Path, recursive: Boolean=false): Unit = {
    if (Files.isDirectory(file)) {
      file.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
      if (recursive) {
        Files.list(file).iterator().foreach(f => watch(f, recursive))
      }
    }
  }

  protected[this] def reactTo(target: Path) = Files.isDirectory(root) || (root == target)

  protected[this] def process(key: WatchKey) = {
    key.pollEvents() forEach {
      case event: WatchEvent[Path] =>
        val target = event.context()
        if(reactTo(target)) {
          if (Files.isDirectory(root) && event.kind() == ENTRY_CREATE) {
            watch(root.resolve(target))
          }
          dispatch(event.kind(), target)
        }
      case event => onUnknownEvent(event)
    }
    key.reset()
  }

  def dispatch(eventType: WatchEvent.Kind[Path], file: Path): Unit = {
    eventType match {
      case ENTRY_CREATE => onCreate(file)
      case ENTRY_MODIFY => onModify(file)
      case ENTRY_DELETE => onDelete(file)
    }
  }
}

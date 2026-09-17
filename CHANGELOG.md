# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading this module (`/upm uninstall UltiTools-Chat`, or server shutdown) now runs the
  framework's command unregistration and then its listener unregistration; this module has no
  unload work of its own. Previously this module's empty unload method replaced the framework's,
  so its commands were never unregistered on any unload path, and its listeners were not
  unregistered on `/upm uninstall` (UltiKits/UltiChat#23).
- 卸载本模块（`/upm uninstall UltiTools-Chat` 或关闭服务器）现在会先由框架注销命令，再注销监听器；
  本模块自身没有卸载工作。此前本模块的空卸载方法替换了框架的卸载方法，因此任何卸载途径都不会注销其
  命令，`/upm uninstall` 也不会注销其监听器（UltiKits/UltiChat#23）。

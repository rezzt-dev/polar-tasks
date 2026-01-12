# Polar.
<div align="center">

<img src="https://img.shields.io/badge/polar-minimalist%20task%20manager-3DDC84?style=for-the-badge&logo=android&logoColor=white" />

<br>

<strong>Organize your daily life efficiently with a minimalist and powerful task manager</strong>

<br>

<a href="https://opensource.org/licenses/MIT">
  <img src="https://img.shields.io/badge/license-mit-yellow.svg" />
</a>
<a href="https://kotlinlang.org">
  <img src="https://img.shields.io/badge/kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white" />
</a>
<a href="https://developer.android.com">
  <img src="https://img.shields.io/badge/platform-android-3DDC84?logo=android&logoColor=white" />
</a>
<a href="https://m3.material.io">
  <img src="https://img.shields.io/badge/design-material%203-757575?logo=materialdesign&logoColor=white" />
</a>

<br>

<a href="#">View Demo</a> •
<a href="./issues">Report a Bug</a> •
<a href="./issues">Request a Feature</a>

</div>

---

## Table Of Contents
- [overview](#overview)
- [features](#features)
- [demo](#demo)
- [technologies](#technologies)
- [installation](#installation)
- [project structure](#project-structure)
- [download](#download)
- [contributing](#contributing)
- [license](#license)
- [author](#author)

---
## Overview
**polar** is a native android application designed to simplify personal task management. it combines a clean, distraction-free interface with powerful organizational tools like nested subtasks, custom tagging, and calendar views. built entirely with modern kotlin and following clean architecture principles, it ensures performance, stability, and offline availability.
### Use Cases
- **daily planning**: track your habits and daily to-do lists without clutter.
- **project tracking**: break down complex projects using subtasks and tags.
- **deadline management**: visualize your schedule with the integrated calendar view.
- **personal organization**: keep your work and personal life separate with custom lists.

---
## Features

### Core Functionality
- **smart organization**: create multiple lists to categorize duties (e.g., work, personal, groceries).
- **detailed tasks**: rich task creation with descriptions, subtasks, and due dates.
- **advanced tagging**: color-coded tags for quick visual filtering and priority setting.
- **calendar integration**: seamless switch between list view and monthly calendar view.
- **universal search**: instantly find tasks, lists, or tags with a powerful local search.
- **offline first**: complete functionality without an internet connection using local persistence.

### technical highlights
- **mvvm architecture**: clear separation of concerns for maintainable code.
- **modern android**: built with jetpack libraries, coroutines, and flow.
- **reactive ui**: updates propagate instantly across the app.
- **dark/light mode**: adaptive ui that respects system preferences.

---
## Demo

### Workflows
> add application screenshots in this section.
example flow:
`create list → add task with tags → set due date → view in calendar`

---
## Technologies

### Android Development
- **kotlin**: 100% kotlin codebase.
- **android sdk**: utilizing the latest android apis.
- **jetpack components**:
  - **viewmodel**: ui-related data holder.
  - **livedata / flow**: observable data holders.
  - **room database**: sqlite abstraction for local data persistence.
  - **navigation component**: handling in-app navigation.
  - **viewbinding**: safe interaction with views.

### Architecture
- **pattern**: model-view-viewmodel (mvvm).
- **dependency injection**: hilt (recommended integration).
- **asynchronous operations**: kotlin coroutines.

---
## Installation

### Prerequisites
- android studio (flamingo or newer).
- jdk 17.
- android sdk api level 26+.

### Local setup
1. **Clone the repository**
```bash
git clone https://github.com/rezzt-devc/polar.git
cd polar
```
2. Ppen in android studio select "open an existing project" and choose the polar directory.
3. Sync gradle allow android studio to download dependencies and index the project.
4. Run the app connect a device or start an emulator and press run (shift + f10).

---
## Project Structure
```txt
app/src/main/java/app/polar/
│
├── data/                  # data layer
│   ├── dao/               # database access objects
│   ├── entity/            # room database entities
│   ├── model/             # domain models
│   └── repository/        # data repositories
│
├── ui/                    # presentation layer
│   ├── activity/          # app entry points and containers
│   ├── adapter/           # recyclerview adapters
│   ├── dialog/            # modal dialogs and bottom sheets
│   ├── fragment/          # ui screens
│   └── viewmodel/         # state management
│
├── util/                  # helper classes and extensions
│
└── mainactivity.kt        # single activity entry point
```

---
## Contributing
contributions are always welcome to make polar better.
how to contribute:

1. __fork__ the repository.
2. __create__ a branch (`git checkout -b feature/amazingfeature`).
3. __commit__ your changes (`git commit -m 'add some amazingfeature'`).
4. __push__ to the branch (`git push origin feature/amazingfeature`).
5. __open__ a pull request.

---
## License
this project is licensed under the mit license. see the license file for full details.

---
## Author
__rezzt.dev__
- website: [rezzt.dev](https://rezzt.dev)
- github: [@rezzt-dev](https://github.com/rezzt-dev)

---
<div align="center">
developed by <a href="https://rezzt.dev" type"_blank"><b>rezzt.dev</b></a>
</div>

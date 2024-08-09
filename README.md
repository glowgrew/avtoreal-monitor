# avtoreal-monitor
A driving-school schedule observer that notifies about schedule windows

## Features:
- **Time Range Checker**: Determines if the current time is within a specified window on a particular day of the week.
- **Time Zone Support**: Supports checking time ranges in specific time zones (e.g., UTC+5).
- **Configurable Parameters**: Easily adjustable constants for window duration, target day, target time, and time zone.
- **Telegram support**: Sends an available schedule window to the specified Telegram chat
## Usage

### Prerequisites
- JDK 21 or later

### Installation
1. Clone the repository:
    ```sh
    git clone https://github.com/glowgrew/avtoreal-monitor.git
    ```
2. Navigate to the project directory:
    ```sh
    cd avtoreal-monitor
    ```
3. Adjust the parameters in `application.yml` or create a new file `application-secret.yml`

4. Run the project:
    ```sh
    ./gradlew bootRun
    ```
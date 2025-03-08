.PHONY: build run clean

# Build project using Gradle
build:
	gradle clean build

# Run the application with specific arguments
run: build
	gradle run --args="-b 1 ./conf/settings_randomwaypoint-snw.txt" --stacktrace

# Clean the project
clean:
	gradle clean
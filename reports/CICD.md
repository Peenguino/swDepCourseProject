# Lab 1 - Continuous Integration & Continuous Delivery

## CI Workflow Description

For this task we worked with Github Actions, we created a [**workflow**](../.github/workflows/ci.yml) based on some **steps**:
- **OS Environment**: We took `ubuntu-latest` as reference for the OS for this workflow.
- **Triggers**: The workflow starts on `push` and `pull_request` **events** on *master* and *main* **branches**.
- **Checkout**: We got the current code from the repo through an action from the marketplace.
- **JDK**: Same with JDK, we took a version through the usage of an action from the marketplace.
- **Testing**: Effective action that runs the tests in clean and batching mode, so we can read the logs and we are not using old artifacts but.

So each action allows us to do a step in setupping the same environment to run the test cases when an event, `pull_request` or `push`, happens on the targeted branches.

## Build Reproducibility and Code Quality through Pipeline usage

- **Reproducibility**: For each run we are defining, in a clean way, the ubuntu version, JDK with specific arguments and the test not based on older artifacts. In this way we can isolate the effects of the tests from the environment, that is always setupped in the same way for each run.
- **Code Quality**: The workflow's goal is to run automatically tests for each push pull request on the targeted branches.
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.jetbrains.python.testing.universalTests

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.testing.PythonTestConfigurationsModel

/**
 * unittest
 */

class PyUniversalUnitTestSettingsEditor(configuration: PyUniversalTestConfiguration) :
  PyUniversalTestSettingsEditor(PyUniversalTestForm.create(configuration))

class PyUniversalUnitTestExecutionEnvironment(configuration: PyUniversalUnitTestConfiguration, environment: ExecutionEnvironment) :
  PyUniversalTestExecutionEnvironment<PyUniversalUnitTestConfiguration>(configuration, environment) {
  override fun getRunner() = PythonHelper.UNITTEST
}


class PyUniversalUnitTestConfiguration(project: Project, factory: PyUniversalTestFactory) :
  PyUniversalTestConfiguration(project, factory, runBareFunctions = false) { // Bare functions not supported in unittest: classes only
  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
    PyUniversalUnitTestExecutionEnvironment(this, environment)

  override fun createConfigurationEditor(): SettingsEditor<PyUniversalTestConfiguration> =
    PyUniversalUnitTestSettingsEditor(this)

}

object PyUniversalUnitTestFactory : PyUniversalTestFactory() {
  override fun createTemplateConfiguration(project: Project) = PyUniversalUnitTestConfiguration(project, this)

  override fun getName(): String = PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME
}
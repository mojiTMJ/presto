/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.tests.product.launcher.cli;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import io.prestosql.tests.product.launcher.Extensions;
import io.prestosql.tests.product.launcher.LauncherModule;
import io.prestosql.tests.product.launcher.PathResolver;
import io.prestosql.tests.product.launcher.env.EnvironmentConfig;
import io.prestosql.tests.product.launcher.env.EnvironmentConfigFactory;
import io.prestosql.tests.product.launcher.env.EnvironmentModule;
import io.prestosql.tests.product.launcher.env.EnvironmentOptions;
import io.prestosql.tests.product.launcher.suite.Suite;
import io.prestosql.tests.product.launcher.suite.SuiteFactory;
import io.prestosql.tests.product.launcher.suite.SuiteModule;
import io.prestosql.tests.product.launcher.suite.SuiteTestRun;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import javax.inject.Inject;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Paths;

import static io.prestosql.tests.product.launcher.cli.Commands.runCommand;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static picocli.CommandLine.Option;

@Command(
        name = "describe",
        description = "Describe tests suite",
        usageHelpAutoWidth = true)
public class SuiteDescribe
        implements Runnable
{
    private final Module additionalSuites;
    private final Module additionalEnvironments;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit")
    public boolean usageHelpRequested;

    @Mixin
    public SuiteDescribeOptions options = new SuiteDescribeOptions();

    @Mixin
    public EnvironmentOptions environmentOptions = new EnvironmentOptions();

    public SuiteDescribe(Extensions extensions)
    {
        this.additionalSuites = requireNonNull(extensions, "extensions is null").getAdditionalSuites();
        this.additionalEnvironments = requireNonNull(extensions, "extensions is null").getAdditionalEnvironments();
    }

    @Override
    public void run()
    {
        runCommand(
                ImmutableList.<Module>builder()
                        .add(new LauncherModule())
                        .add(new SuiteModule(additionalSuites))
                        .add(new EnvironmentModule(environmentOptions, additionalEnvironments))
                        .add(options.toModule())
                        .build(),
                SuiteDescribe.Execution.class);
    }

    public static class SuiteDescribeOptions
    {
        @Option(names = "--suite", paramLabel = "<suite>", description = "Name of the suite to describe", required = true)
        public String suite;

        public Module toModule()
        {
            return binder -> binder.bind(SuiteDescribeOptions.class).toInstance(this);
        }
    }

    public static class Execution
            implements Runnable
    {
        private final String suiteName;
        private final String config;
        private final SuiteFactory suiteFactory;
        private final EnvironmentConfigFactory configFactory;
        private final EnvironmentOptions environmentOptions;
        private final PathResolver pathResolver;
        private final PrintStream out;

        @Inject
        public Execution(
                SuiteDescribeOptions describeOptions,
                SuiteFactory suiteFactory,
                EnvironmentConfigFactory configFactory,
                PathResolver pathResolver,
                EnvironmentOptions environmentOptions)
        {
            this.suiteName = requireNonNull(describeOptions.suite, "describeOptions.suite is null");
            this.config = requireNonNull(environmentOptions.config, "environmentOptions.config is null");
            this.suiteFactory = requireNonNull(suiteFactory, "suiteFactory is null");
            this.configFactory = requireNonNull(configFactory, "configFactory is null");
            this.pathResolver = requireNonNull(pathResolver, "pathResolver is null");
            this.environmentOptions = requireNonNull(environmentOptions, "environmentOptions is null");

            try {
                this.out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, Charset.defaultCharset().name());
            }
            catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("Could not create print stream", e);
            }
        }

        @Override
        public void run()
        {
            Suite suite = suiteFactory.getSuite(suiteName);
            EnvironmentConfig config = configFactory.getConfig(this.config);

            out.println(format("Suite '%s' with configuration '%s' consists of following test runs: ", suiteName, this.config));

            for (SuiteTestRun testRun : suite.getTestRuns(config)) {
                TestRun.TestRunOptions runOptions = createTestRunOptions(suiteName, testRun, config);
                out.println(format("\npresto-product-tests-launcher/bin/run-launcher test run %s\n", OptionsPrinter.format(environmentOptions, runOptions)));
            }
        }

        private TestRun.TestRunOptions createTestRunOptions(String suiteName, SuiteTestRun suiteTestRun, EnvironmentConfig environmentConfig)
        {
            TestRun.TestRunOptions testRunOptions = new TestRun.TestRunOptions();
            testRunOptions.environment = suiteTestRun.getEnvironmentName();
            testRunOptions.testArguments = suiteTestRun.getTemptoRunArguments(environmentConfig);
            testRunOptions.testJar = pathResolver.resolvePlaceholders(testRunOptions.testJar);
            testRunOptions.reportsDir = Paths.get(format("presto-product-tests/target/%s/%s/%s", suiteName, environmentConfig.getConfigName(), suiteTestRun.getEnvironmentName()));
            return testRunOptions;
        }
    }
}

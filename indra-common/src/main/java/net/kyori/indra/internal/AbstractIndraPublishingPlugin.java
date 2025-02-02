/*
 * This file is part of indra, licensed under the MIT License.
 *
 * Copyright (c) 2020-2022 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.indra.internal;

import java.util.Objects;
import java.util.Set;
import net.kyori.indra.Indra;
import net.kyori.indra.api.model.ContinuousIntegration;
import net.kyori.indra.api.model.Issues;
import net.kyori.indra.api.model.License;
import net.kyori.indra.api.model.SourceCodeManagement;
import net.kyori.indra.git.GitPlugin;
import net.kyori.indra.git.task.RequireClean;
import net.kyori.indra.util.Versioning;
import net.kyori.mammoth.ProjectPlugin;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.signing.Sign;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.SigningPlugin;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractIndraPublishingPlugin implements ProjectPlugin {
  private static final String FORCE_SIGN_PROPERTY = "forceSign";

  @Override
  public @Nullable GradleVersion minimumGradleVersion() {
    return Indra.MINIMUM_SUPPORTED;
  }

  @Override
  public void apply(final @NotNull Project project, final @NotNull PluginContainer plugins, final @NotNull ExtensionContainer extensions, final @NotNull TaskContainer tasks) {
    plugins.apply(MavenPublishPlugin.class);
    plugins.apply(SigningPlugin.class);
    plugins.apply(GitPlugin.class);

    final IndraExtensionImpl indra = (IndraExtensionImpl) Indra.extension(extensions);

    final Project rootProject = project.getRootProject();
    if (!Objects.equals(project, rootProject)) {
      project.setGroup(rootProject.getGroup());
      project.setVersion(rootProject.getVersion());
      project.setDescription(rootProject.getDescription());
    }

    this.configurePublications(extensions.getByType(PublishingExtension.class), publication -> {
      publication.pom(pom -> {
        pom.getName().set(project.getName());
        pom.getDescription().set(project.provider(project::getDescription));
        pom.getUrl().set(indra.scm().map(SourceCodeManagement::url));

        pom.ciManagement(ci -> {
          ci.getSystem().set(indra.ci().map(ContinuousIntegration::system));
          ci.getUrl().set(indra.ci().map(ContinuousIntegration::url));
        });

        pom.issueManagement(issues -> {
          issues.getSystem().set(indra.issues().map(Issues::system));
          issues.getUrl().set(indra.issues().map(Issues::url));
        });

        pom.licenses(licenses -> {
          licenses.license(license -> {
            license.getName().set(indra.license().map(License::name));
            license.getUrl().set(indra.license().map(License::url));
          });
        });

        pom.scm(scm -> {
          scm.getConnection().set(indra.scm().map(SourceCodeManagement::connection));
          scm.getDeveloperConnection().set(indra.scm().map(SourceCodeManagement::developerConnection));
          scm.getUrl().set(indra.scm().map(SourceCodeManagement::url));
        });
      });
    });

    // Code signing
    extensions.configure(SigningExtension.class, extension -> {
      extension.sign(extensions.getByType(PublishingExtension.class).getPublications());
      indra.initSigningExtension(extension);
      if (!indra.alternateSigningConfigured()) {
        extension.useGpgCmd();
      }
    });

    tasks.withType(Sign.class).configureEach(task -> {
      final Provider<Boolean> forceSign = project.getProviders().gradleProperty(FORCE_SIGN_PROPERTY).map(x -> true).orElse(false);
      final Provider<Boolean> isRelease = project.getProviders().provider(() -> Versioning.isRelease(project));
      task.onlyIf(spec -> forceSign.zip(isRelease, (a, b) -> a || b).get());
    });

    final TaskProvider<RequireClean> requireClean = tasks.named(GitPlugin.REQUIRE_CLEAN_TASK, RequireClean.class);
    tasks.withType(AbstractPublishToMaven.class).configureEach(task -> {
      if (!(task instanceof PublishToMavenLocal)) {
        task.dependsOn(requireClean);
      }
    });

    project.afterEvaluate(p -> {
      extensions.configure(PublishingExtension.class, publishing -> {
        this.applyPublishingActions(publishing, indra.publishingActions);

        indra.repositories.all(rr -> { // will be applied to repositories as they're added
          if (this.canPublishTo(project, rr)) {
            publishing.getRepositories().maven(repository -> {
              repository.setName(rr.name());
              repository.setUrl(rr.url());
              // ${id}Username + ${id}Password properties
              repository.credentials(PasswordCredentials.class);
            });
          }
        });
      });
    });

    this.extraApplySteps(project);
  }

  @SuppressWarnings("RedundantIfStatement")
  private boolean canPublishTo(final Project project, final RemoteRepository repository) {
    // as per PasswordCredentials
    final String username = repository.name() + "Username";
    final String password = repository.name() + "Password";

    if (!project.hasProperty(username) || !project.hasProperty(password)) {
      project.getLogger().info("indra-publishing: skipping repository {} because username or password was not set", repository.name());
      return false;
    }

    if (repository.releases() && Versioning.isRelease(project)) {
      project.getLogger().info("indra-publishing: adding repository {} because it accepts releases and this project is in a release state", repository.name());
      return true;
    }
    if (repository.snapshots() && Versioning.isSnapshot(project)) {
      project.getLogger().info("indra-publishing: adding repository {} because it accepts snapshots and this project is in a snapshot state", repository.name());
      return true;
    }

    project.getLogger().info("indra-publishing: skipping repository {} because release/snapshot constraint not met", repository.name());
    return false;
  }

  /**
   * Add any extra steps sub-plugins might want to perform on application.
   *
   * @param project the project to target
   */
  protected void extraApplySteps(final Project project) {
  }

  /**
   * Apply publishing actions to all publications targeted.
   *
   * @param extension the publishing extension to apply to
   * @param actions actions to configure the publication with
   */
  protected abstract void applyPublishingActions(final PublishingExtension extension, final Set<Action<MavenPublication>> actions);

  /**
   * Configure and/or create publications, applying the provided common configuration action.
   *
   * @param extension the publishing extension to apply to
   * @param action action to configure the publication with
   */
  protected abstract void configurePublications(final PublishingExtension extension, final Action<MavenPublication> action);
}

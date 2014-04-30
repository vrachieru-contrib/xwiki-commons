/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.extension.repository.aether.internal;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.eclipse.aether.util.repository.JreProxySelector;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.extension.ExtensionManagerConfiguration;
import org.xwiki.extension.repository.AbstractExtensionRepositoryFactory;
import org.xwiki.extension.repository.ExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.ExtensionRepositoryException;

import com.google.common.io.Files;

/**
 * @version $Id$
 * @since 4.0M1
 */
@Component
@Singleton
@Named("maven")
public class AetherExtensionRepositoryFactory extends AbstractExtensionRepositoryFactory implements Initializable
{
    static final JreProxySelector JREPROXYSELECTOR = new JreProxySelector();

    @Inject
    private ComponentManager componentManager;

    @Inject
    private Provider<PlexusContainer> plexusProvider;

    @Inject
    private ExtensionManagerConfiguration configuration;

    @Inject
    private Logger logger;

    private RepositorySystem repositorySystem;

    @Override
    public void initialize() throws InitializationException
    {
        try {
            this.repositorySystem = this.plexusProvider.get().lookup(RepositorySystem.class);
        } catch (ComponentLookupException e) {
            throw new InitializationException("Failed to lookup RepositorySystem", e);
        }
    }

    public RepositorySystemSession createRepositorySystemSession()
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        File localDir = Files.createTempDir();
        LocalRepository localRepository = new LocalRepository(localDir);
        session.setLocalRepositoryManager(this.repositorySystem.newLocalRepositoryManager(session, localRepository));
        session.setConfigProperty(ConfigurationProperties.USER_AGENT, this.configuration.getUserAgent());
        session.setProxySelector(JREPROXYSELECTOR);

        // Remove all system properties that could disrupt effective pom resolution
        session.setSystemProperty("version", null);
        session.setSystemProperty("groupId", null);

        // Add various type descriptors
        ArtifactTypeRegistry artifactTypeRegistry = session.getArtifactTypeRegistry();
        if (artifactTypeRegistry instanceof DefaultArtifactTypeRegistry) {
            DefaultArtifactTypeRegistry defaultArtifactTypeRegistry =
                (DefaultArtifactTypeRegistry) artifactTypeRegistry;
            defaultArtifactTypeRegistry.add(new DefaultArtifactType("bundle", "jar", "", "java"));
            defaultArtifactTypeRegistry.add(new DefaultArtifactType("eclipse-plugin", "jar", "", "java"));
        }

        // Fail when the pom is missing or invalid
        session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(false, false));

        return session;
    }

    public static void dispose(RepositorySystemSession session)
    {
        LocalRepository repository = session.getLocalRepository();

        if (repository.getBasedir().exists()) {
            try {
                FileUtils.deleteDirectory(repository.getBasedir());
            } catch (IOException e) {
                // TODO: Should probably log something even if it should be pretty rare
            }
        }
    }

    @Override
    public ExtensionRepository createRepository(ExtensionRepositoryDescriptor repositoryDescriptor)
        throws ExtensionRepositoryException
    {
        try {
            return new AetherExtensionRepository(repositoryDescriptor, this, this.plexusProvider.get(),
                this.componentManager);
        } catch (Exception e) {
            throw new ExtensionRepositoryException("Failed to create repository [" + repositoryDescriptor + "]", e);
        }
    }
}

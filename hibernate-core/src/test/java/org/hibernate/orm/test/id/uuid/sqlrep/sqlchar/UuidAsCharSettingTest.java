/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.id.uuid.sqlrep.sqlchar;

import java.sql.Types;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.mapping.Property;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.DomainModelScope;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Steve Ebersole
 */
@ServiceRegistry( settings = @Setting( name = AvailableSettings.PREFERRED_UUID_JDBC_TYPE, value = "CHAR" ) )
@DomainModel(annotatedClasses = { UuidAsCharSettingTest.Node.class })
@SessionFactory
public class UuidAsCharSettingTest {

	private static class UUIDPair {
		UUID rootId;
		UUID childId;

		public UUIDPair(UUID rootId, UUID childId) {
			this.rootId = rootId;
			this.childId = childId;
		}
	}

	@Test
	public void testSchema(DomainModelScope domainModelScope) {
		domainModelScope.withHierarchy( Node.class, (entityDescriptor) -> {
			final JdbcTypeRegistry jdbcTypeRegistry = domainModelScope.getDomainModel()
					.getTypeConfiguration()
					.getJdbcTypeRegistry();
			final Property identifierProperty = entityDescriptor.getIdentifierProperty();
			final BasicType<?> uuidType = (BasicType<?>) identifierProperty.getType();
			assertThat( uuidType.getJdbcType(), is( jdbcTypeRegistry.getDescriptor( Types.CHAR ) ) );
		} );
	}

	@Test
	public void testUsage(SessionFactoryScope scope) {
		final MappingMetamodel domainModel = scope.getSessionFactory().getRuntimeMetamodels().getMappingMetamodel();
		final EntityPersister entityDescriptor = domainModel.findEntityDescriptor( Node.class );
		final List<JdbcMapping> identifierJdbcMappings = entityDescriptor.getIdentifierMapping().getJdbcMappings();
		assertThat( identifierJdbcMappings, hasSize( 1 ) );
		final JdbcMapping jdbcMapping = identifierJdbcMappings.get( 0 );
		assertThat( jdbcMapping.getJdbcType().isString(), is( true ) );

		final UUIDPair uuidPair = scope.fromTransaction( session -> {
			final Node root = new Node( "root" );
			session.save( root );
			assertThat( root.id, notNullValue());

			final Node child = new Node( "child", root );
			session.save( child );
			assertThat( child.id, notNullValue() );

			return new UUIDPair( root.id, child.id );
		} );

		scope.inTransaction( session -> {
			final Node root = session.get( Node.class, uuidPair.rootId );
			assertThat( root, notNullValue() );
			final Node child = session.get( Node.class, uuidPair.childId );
			assertThat( child, notNullValue() );
		} );

		scope.inTransaction( session -> {
			final Node node = session.createQuery( "from Node n join fetch n.parent where n.parent is not null", Node.class ).uniqueResult();
			assertThat( node, notNullValue() );
			assertThat( node.parent, notNullValue() );
		} );
	}

	@Entity(name = "Node")
	static class Node {

		@Id
		@GeneratedValue
		UUID id;

		String name;

		@ManyToOne
		Node parent;

		Node() {
		}

		Node(String name) {
			this.name = name;
		}

		Node(String name, Node parent) {
			this.name = name;
			this.parent = parent;
		}
	}
}
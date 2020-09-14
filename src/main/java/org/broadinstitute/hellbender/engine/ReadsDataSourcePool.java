package org.broadinstitute.hellbender.engine;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.utils.AutoCloseableReference;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pool of {@link ReadsDataSource} instances.
 */
public final class ReadsDataSourcePool extends GenericObjectPool<ReadsDataSource> implements AutoCloseable {

    public ReadsDataSourcePool(final List<Path> readPaths) {
        super(new Factory(readPaths));
        setWhenExhaustedAction(WHEN_EXHAUSTED_GROW);
    }

    /**
     * Returns a reads-data-source wrapped into a reference that when close returns
     * the source back to the pool.
     * @return never {@code null}.
     */
    public AutoCloseableReference<ReadsDataSource> borrowAutoReturn() {
        return AutoCloseableReference.of(borrowObject(), this::returnObject);
    }

    @Override
    public ReadsDataSource borrowObject() {
        try {
            return super.borrowObject();
        } catch (Exception e) {
            throw new GATKException(e.getMessage(), e);
        }
    }

    @Override
    public void returnObject(final ReadsDataSource dataSource) {
        try {
            super.returnObject(dataSource);
        } catch (Exception e) {
            throw new GATKException(e.getMessage(), e);
        }
    }

    public void close() {
        try {
            super.close();
        } catch (final Exception ex) {
            throw new GATKException("exception when closing the pool", ex);
        }
    }

    private static class Factory extends BasePoolableObjectFactory<ReadsDataSource> {

        private final List<Path> paths;

        private Factory(final List<Path> paths) {
            this.paths = new ArrayList<>(paths);
        }

        @Override
        public ReadsDataSource makeObject() {
            return new ReadsDataSource(paths);
        }

        @Override
        public void destroyObject(final ReadsDataSource dataSource) {
            dataSource.close();
        }

        @Override
        public boolean validateObject(final ReadsDataSource dataSource) {
            return !dataSource.isClosed();
        }
    }
}

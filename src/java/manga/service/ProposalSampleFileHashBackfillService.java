package manga.service;

import java.io.File;
import java.util.Map;
import javax.servlet.ServletContext;
import manga.common.util.FileHashUtil;
import manga.repository.ProposalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Fills in {@code Proposal.sampleFileHash} for proposals uploaded before duplicate-file
 * detection existed.
 *
 * <p>Without this, an old proposal's file would have a {@code NULL} hash and a Mangaka could
 * re-upload that exact file without being blocked. The backfill hashes the files still present
 * under the webapp upload directory and runs once per application start, lazily, the first time
 * someone actually uploads a sample file.</p>
 */
@Service
public class ProposalSampleFileHashBackfillService {

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired(required = false)
    private ServletContext servletContext;

    private volatile boolean completed;

    /** Runs the backfill once; further calls return immediately. */
    public void ensureBackfilled() {
        if (completed) {
            return;
        }
        synchronized (this) {
            if (completed) {
                return;
            }
            // Marked done up front: a failure here must not re-scan on every single upload.
            completed = true;
            try {
                backfill();
            } catch (RuntimeException ignored) {
                // Duplicate detection still works for every file uploaded from now on.
            }
        }
    }

    private void backfill() {
        if (servletContext == null) {
            return;
        }
        Map<Long, String> missing = proposalRepository.findSampleFilesMissingHash();
        for (Map.Entry<Long, String> entry : missing.entrySet()) {
            String realPath = servletContext.getRealPath(entry.getValue());
            if (realPath == null) {
                continue;
            }
            File file = new File(realPath);
            if (!file.isFile()) {
                // File no longer on disk (seed data or a wiped uploads folder) — nothing to hash.
                continue;
            }
            String hash = FileHashUtil.sha256Hex(file);
            if (hash != null) {
                proposalRepository.updateSampleFileHash(entry.getKey().longValue(), hash);
            }
        }
    }
}

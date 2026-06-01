package manga.scheduler;

import manga.repository.ProposalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProposalBoardVotingScheduler {

    @Autowired
    private ProposalRepository proposalRepository;

    // Run every 15 minutes to close proposal board rounds whose 3-day window ended.
    @Scheduled(cron = "0 */15 * * * *")
    public void closeExpiredBoardVotingRounds() {
        proposalRepository.closeExpiredBoardVotingRounds();
    }

    // Run hourly to remind board members who have not voted when a round has <=24h left.
    @Scheduled(cron = "0 0 * * * *")
    public void remindBoardVotingClosingSoon() {
        proposalRepository.notifyBoardVotingClosingSoon();
    }

    // Run hourly to flag Tantou proposal reviews that have exceeded the 48h SLA.
    @Scheduled(cron = "0 5 * * * *")
    public void markOverdueTantouReviews() {
        proposalRepository.markOverdueTantouReviews();
    }
}

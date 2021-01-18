package com.relino.core.register;

import com.relino.core.config.LeaderSelectorConfig;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 基于CuratorLeader的Leader节点选取
 *
 * @author kaiqiang.he
 */
public class CuratorLeaderElection {

    private static final Logger log = LoggerFactory.getLogger(CuratorLeaderElection.class);

    private List<RelinoLeaderElectionListener> leaderSelectorListener;
    private CuratorFramework curatorClient;

    public CuratorLeaderElection(List<RelinoLeaderElectionListener> leaderSelectorListener, CuratorFramework curatorClient) {
        this.leaderSelectorListener = leaderSelectorListener;
        this.curatorClient = curatorClient;
    }

    public void execute() {
        for (RelinoLeaderElectionListener listener : leaderSelectorListener) {
            LeaderSelectorConfig config = listener.getLeaderSelectorConfig();
            LeaderSelector leaderSelector = new LeaderSelector(curatorClient, config.getLeaderPath(), listener);
            leaderSelector.autoRequeue();
            leaderSelector.start();
            log.info("{} leader selection start.", config.getName());
        }
    }
}

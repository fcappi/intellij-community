/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.impl.facade;


import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.openapi.util.Condition;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.collapsing.BranchFilterController;
import com.intellij.vcs.log.graph.collapsing.CollapsedController;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.impl.facade.bek.BekSorter;
import com.intellij.vcs.log.graph.impl.permanent.*;
import com.intellij.vcs.log.graph.linearBek.LinearBekController;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class PermanentGraphImpl<CommitId> implements PermanentGraph<CommitId>, PermanentGraphInfo<CommitId> {
  @NotNull private final PermanentCommitsInfoImpl<CommitId> myPermanentCommitsInfo;
  @NotNull private final PermanentLinearGraphImpl myPermanentLinearGraph;
  @NotNull private final GraphLayoutImpl myPermanentGraphLayout;
  @NotNull private final Set<Integer> myBranchNodeIds;

  @NotNull private final Supplier<BekIntMap> myBekIntMap;

  @NotNull private final GraphColorManager<CommitId> myGraphColorManager;
  @NotNull private final ReachableNodes myReachableNodes;

  public PermanentGraphImpl(@NotNull PermanentLinearGraphImpl permanentLinearGraph,
                            @NotNull GraphLayoutImpl permanentGraphLayout,
                            @NotNull PermanentCommitsInfoImpl<CommitId> permanentCommitsInfo,
                            @NotNull GraphColorManager<CommitId> graphColorManager,
                            @NotNull Set<CommitId> branchesCommitId) {
    myPermanentGraphLayout = permanentGraphLayout;
    myPermanentCommitsInfo = permanentCommitsInfo;
    myPermanentLinearGraph = permanentLinearGraph;
    myGraphColorManager = graphColorManager;
    myBranchNodeIds = permanentCommitsInfo.convertToNodeIds(branchesCommitId);
    myReachableNodes = new ReachableNodes(LinearGraphUtils.asLiteLinearGraph(permanentLinearGraph));
    myBekIntMap = Suppliers.memoize(
      () -> BekSorter.createBekMap(myPermanentLinearGraph, myPermanentGraphLayout, myPermanentCommitsInfo.getTimestampGetter()));
  }

  /**
   * Create new instance of PermanentGraph.
   *
   * @param graphCommits      topologically sorted list of commits in the graph
   * @param graphColorManager color manager for the graph
   * @param branchesCommitId  commit ids of all the branch heads
   * @param <CommitId>        commit id type
   * @return new instance of PermanentGraph
   */
  @NotNull
  public static <CommitId> PermanentGraphImpl<CommitId> newInstance(@NotNull List<? extends GraphCommit<CommitId>> graphCommits,
                                                                    @NotNull GraphColorManager<CommitId> graphColorManager,
                                                                    @NotNull Set<CommitId> branchesCommitId) {
    PermanentLinearGraphBuilder<CommitId> permanentLinearGraphBuilder = PermanentLinearGraphBuilder.newInstance(graphCommits);
    NotLoadedCommitsIdsGenerator<CommitId> idsGenerator = new NotLoadedCommitsIdsGenerator<>();
    PermanentLinearGraphImpl linearGraph = permanentLinearGraphBuilder.build(idsGenerator);

    final PermanentCommitsInfoImpl<CommitId> commitIdPermanentCommitsInfo =
      PermanentCommitsInfoImpl.newInstance(graphCommits, idsGenerator.getNotLoadedCommits());

    GraphLayoutImpl permanentGraphLayout = GraphLayoutBuilder.build(linearGraph, (nodeIndex1, nodeIndex2) -> {
      CommitId commitId1 = commitIdPermanentCommitsInfo.getCommitId(nodeIndex1);
      CommitId commitId2 = commitIdPermanentCommitsInfo.getCommitId(nodeIndex2);
      return graphColorManager.compareHeads(commitId1, commitId2);
    });

    return new PermanentGraphImpl<>(linearGraph, permanentGraphLayout, commitIdPermanentCommitsInfo, graphColorManager,
                                    branchesCommitId);
  }

  @NotNull
  private LinearGraphController createBaseController(@NotNull SortType sortType) {
    if (sortType == SortType.Normal) {
      return new BaseController(this);
    }
    else if (sortType == SortType.LinearBek) {
      return new LinearBekController(new BekBaseController(this, myBekIntMap.get()), this);
    }
    return new BekBaseController(this, myBekIntMap.get());
  }

  @NotNull
  private LinearGraphController createFilteredController(@NotNull LinearGraphController baseController,
                                                         @NotNull SortType sortType,
                                                         @Nullable Set<CommitId> visibleHeads, @Nullable Set<CommitId> matchingCommits) {
    LinearGraphController controller;
    if (matchingCommits != null) {
      controller = new FilteredController(baseController, this, myPermanentCommitsInfo.convertToNodeIds(matchingCommits));
      if (visibleHeads != null) {
        return new BranchFilterController(controller, this, myPermanentCommitsInfo.convertToNodeIds(visibleHeads, true));
      }
      return controller;
    }

    if (sortType == SortType.LinearBek) {
      if (visibleHeads != null) {
        return new BranchFilterController(baseController, this, myPermanentCommitsInfo.convertToNodeIds(visibleHeads, true));
      }
      return baseController;
    }

    if (visibleHeads != null) {
      return new CollapsedController(baseController, this, myPermanentCommitsInfo.convertToNodeIds(visibleHeads, true));
    }
    return new CollapsedController(baseController, this, null);
  }

  @NotNull
  public VisibleGraph<CommitId> createVisibleGraph(@NotNull SortType sortType,
                                                   @Nullable Set<CommitId> visibleHeads,
                                                   @Nullable Set<CommitId> matchingCommits,
                                                   @NotNull BiConsumer<LinearGraphController, PermanentGraphInfo<CommitId>> preprocessor) {
    LinearGraphController controller = createFilteredController(createBaseController(sortType), sortType, visibleHeads, matchingCommits);
    preprocessor.accept(controller, this);
    return new VisibleGraphImpl<>(controller, this, myGraphColorManager);
  }

  @NotNull
  @Override
  public VisibleGraph<CommitId> createVisibleGraph(@NotNull SortType sortType,
                                                   @Nullable Set<CommitId> visibleHeads,
                                                   @Nullable Set<CommitId> matchingCommits) {
    return createVisibleGraph(sortType, visibleHeads, matchingCommits, (controller, info) -> {
    });
  }

  @NotNull
  @Override
  public List<GraphCommit<CommitId>> getAllCommits() {
    List<GraphCommit<CommitId>> result = ContainerUtil.newArrayList();
    for (int index = 0; index < myPermanentLinearGraph.nodesCount(); index++) {
      CommitId commitId = myPermanentCommitsInfo.getCommitId(index);
      List<Integer> downNodes = LinearGraphUtils.getDownNodesIncludeNotLoad(myPermanentLinearGraph, index);
      List<CommitId> parentsCommitIds = myPermanentCommitsInfo.convertToCommitIdList(downNodes);
      GraphCommit<CommitId> graphCommit =
        GraphCommitImpl.createCommit(commitId, parentsCommitIds, myPermanentCommitsInfo.getTimestamp(index));
      result.add(graphCommit);
    }

    return result;
  }

  @NotNull
  @Override
  public List<CommitId> getChildren(@NotNull CommitId commit) {
    int commitIndex = myPermanentCommitsInfo.getNodeId(commit);
    return myPermanentCommitsInfo.convertToCommitIdList(LinearGraphUtils.getUpNodes(myPermanentLinearGraph, commitIndex));
  }

  @NotNull
  @Override
  public Set<CommitId> getContainingBranches(@NotNull CommitId commit) {
    int commitIndex = myPermanentCommitsInfo.getNodeId(commit);
    return myPermanentCommitsInfo.convertToCommitIdSet(myReachableNodes.getContainingBranches(commitIndex, myBranchNodeIds));
  }

  @NotNull
  @Override
  public Condition<CommitId> getContainedInBranchCondition(@NotNull final Collection<CommitId> heads) {
    List<Integer> headIds = ContainerUtil.map(heads, head -> myPermanentCommitsInfo.getNodeId(head));
    if (!heads.isEmpty() && ContainerUtil.getFirstItem(heads) instanceof Integer) {
      final TIntHashSet branchNodes = new TIntHashSet();
      myReachableNodes.walk(headIds, node -> branchNodes.add((Integer)myPermanentCommitsInfo.getCommitId(node)));
      return new IntContainedInBranchCondition<>(branchNodes);
    }
    else {
      final Set<CommitId> branchNodes = ContainerUtil.newHashSet();
      myReachableNodes.walk(headIds, node -> branchNodes.add(myPermanentCommitsInfo.getCommitId(node)));
      return new ContainedInBranchCondition<>(branchNodes);
    }
  }

  @NotNull
  public PermanentCommitsInfoImpl<CommitId> getPermanentCommitsInfo() {
    return myPermanentCommitsInfo;
  }

  @NotNull
  public PermanentLinearGraphImpl getLinearGraph() {
    return myPermanentLinearGraph;
  }

  @NotNull
  public GraphLayoutImpl getPermanentGraphLayout() {
    return myPermanentGraphLayout;
  }

  @NotNull
  public Set<Integer> getBranchNodeIds() {
    return myBranchNodeIds;
  }

  private static class NotLoadedCommitsIdsGenerator<CommitId> implements NotNullFunction<CommitId, Integer> {
    @NotNull private final Map<Integer, CommitId> myNotLoadedCommits = ContainerUtil.newHashMap();

    @NotNull
    @Override
    public Integer fun(CommitId dom) {
      int nodeId = -(myNotLoadedCommits.size() + 2);
      myNotLoadedCommits.put(nodeId, dom);
      return nodeId;
    }

    @NotNull
    public Map<Integer, CommitId> getNotLoadedCommits() {
      return myNotLoadedCommits;
    }
  }

  private static class IntContainedInBranchCondition<CommitId> implements Condition<CommitId> {
    private final TIntHashSet myBranchNodes;

    public IntContainedInBranchCondition(TIntHashSet branchNodes) {
      myBranchNodes = branchNodes;
    }

    @Override
    public boolean value(CommitId commitId) {
      return myBranchNodes.contains((Integer)commitId);
    }
  }

  private static class ContainedInBranchCondition<CommitId> implements Condition<CommitId> {
    private final Set<CommitId> myBranchNodes;

    public ContainedInBranchCondition(Set<CommitId> branchNodes) {
      myBranchNodes = branchNodes;
    }

    @Override
    public boolean value(CommitId commitId) {
      return myBranchNodes.contains(commitId);
    }
  }
}

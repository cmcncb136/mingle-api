package com.kesi.tracker.group.application;

import com.kesi.tracker.core.exception.BusinessException;
import com.kesi.tracker.core.exception.ErrorCode;
import com.kesi.tracker.group.application.repository.GroupMemberRepository;
import com.kesi.tracker.group.domain.GroupMember;
import com.kesi.tracker.group.domain.GroupMemberStatus;
import com.kesi.tracker.group.domain.GroupRole;
import com.kesi.tracker.group.domain.GroupTrackRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMemberServiceImpl implements GroupMemberService {
    private final GroupMemberRepository groupMemberRepository;


    @Override
    public boolean isGroupMember(Long gid, Long uid) {
        return groupMemberRepository.findByGidAndUid(gid, uid)
                .map(GroupMember::isApproved)
                .orElse(false);
    }

    @Override
    public boolean isGroupLeader(Long gid, Long uid) {
        return groupMemberRepository.findByGidAndUid(gid, uid)
                .map(GroupMember::isLeader)
                .orElse(false);
    }


    @Override
    public GroupMember getApprovedByGidAndUid(Long gid, Long uid) {
        GroupMember groupMember = getByGidAndUid(gid, uid);

        if(!groupMember.isApproved())
            throw new BusinessException(ErrorCode.NOT_APPROVED_MEMBER);

        return groupMember;
    }

    @Override
    public Optional<GroupMember> findByGidAndUid(Long gid, Long uid) {
        return groupMemberRepository.findByGidAndUid(gid, uid);
    }

    @Override
    public GroupMember getByGidAndUid(Long gid, Long uid) {
        return this.findByGidAndUid(gid, uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
    }

    @Override
    public GroupMember getById(Long id) {
        return groupMemberRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
    }

    @Override
    public List<GroupMember> findByGidAndRoleIsLeader(Long gid) {
        List<GroupMember> leaderGroupMembers = groupMemberRepository.findByGidAndRole(gid, GroupRole.LEADER);
        if(leaderGroupMembers.isEmpty()) {
            log.error("해당 그룹에 리더가 존재하지 않습니다..? groupId : {}", gid);
        }

        return leaderGroupMembers;
    }

    @Override
    public List<GroupMember> findByGidAndStatus(Long gid, GroupMemberStatus status) {
        return groupMemberRepository.findByGidAndStatus(gid, status);
    }

    @Override
    public List<GroupMember> findByUidAndStatus(Long uid, GroupMemberStatus status) {
        return groupMemberRepository.findByUidAndStatus(uid, status);
    }

    @Override
    public GroupMember createInviteMember(Long gid, Long invitedUid) {
        GroupMember groupMember = GroupMember.builder()
                .gid(gid)
                .uid(invitedUid)
                .role(GroupRole.MEMBER)
                .trackRole(GroupTrackRole.FOLLOWER)
                .status(GroupMemberStatus.INVITED)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();

        return groupMemberRepository.save(groupMember);
    }

    @Override
    public GroupMember createLeaderMember(Long gid, Long leaderUid) {
        GroupMember groupMember = GroupMember.builder()
                .gid(gid)
                .uid(leaderUid)
                .role(GroupRole.LEADER)
                .trackRole(GroupTrackRole.FOLLOWER)
                .status(GroupMemberStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();

        return groupMemberRepository.save(groupMember);
    }

    @Override
    public boolean existsGroupMember(Long gid, Long uid) {
        return groupMemberRepository.existsByGidAndUid(gid, uid);
    }

}

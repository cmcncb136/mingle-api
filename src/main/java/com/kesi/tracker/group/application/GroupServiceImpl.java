package com.kesi.tracker.group.application;

import com.kesi.tracker.core.exception.BusinessException;
import com.kesi.tracker.core.exception.ErrorCode;
import com.kesi.tracker.file.application.FileService;
import com.kesi.tracker.file.domain.FileAccessUrl;
import com.kesi.tracker.file.domain.FileOwner;
import com.kesi.tracker.file.domain.FilePurpose;
import com.kesi.tracker.group.application.dto.*;
import com.kesi.tracker.group.application.mapper.GroupMapper;
import com.kesi.tracker.file.domain.FileOwners;
import com.kesi.tracker.group.application.repository.GroupMemberRepository;
import com.kesi.tracker.group.application.repository.GroupRepository;
import com.kesi.tracker.group.domain.*;
import com.kesi.tracker.group.domain.event.GroupMemberInviteRequestedEvent;
import com.kesi.tracker.group.domain.event.GroupMemberInvitedEvent;
import com.kesi.tracker.group.domain.event.GroupTrackRoleChangedEvent;
import com.kesi.tracker.user.UserMapper;
import com.kesi.tracker.user.application.UserService;
import com.kesi.tracker.user.application.dto.GroupJoinRequestUserProfileResponse;
import com.kesi.tracker.user.application.dto.GroupMemberProfileResponse;
import com.kesi.tracker.user.application.dto.UserProfileResponse;
import com.kesi.tracker.user.domain.ActionActor;
import com.kesi.tracker.user.domain.Email;
import com.kesi.tracker.user.domain.User;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupServiceImpl implements GroupService {
    private final GroupRepository groupRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UserService userService;
    private final GroupMemberService groupMemberService;

    private final GroupMemberRepository groupMemberRepository;
    private final FileService fileService;


    @Override
    public Group create(Group group) {
        return groupRepository.save(group);
    }

    @Override
    public Group getByGid(Long gid) {
        return groupRepository.findByGid(gid).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    @Override
    public Page<Group> searchPublicByName(String name, Pageable pageable) {
        return groupRepository.findByNameContainingIgnoreCaseAndAccess(name, AccessType.PUBLIC, pageable);
    }

    @Override
    @Transactional
    public void updateStatus(Long groupId, Long relationId, GroupMemberStatus status, Long currentUserId) {
        if(!groupMemberService.isGroupLeader(groupId, currentUserId))
            throw new BusinessException(ErrorCode.NOT_GROUP_LEADER);

        GroupMember requestMember = groupMemberService.getById(relationId);
        if(!requestMember.getGid().equals(groupId))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);

        MemberCountChange memberCountChange = requestMember.changeStatusByLeader(status, ActionActor.LEADER);
        groupMemberRepository.save(requestMember);

        processMemberCountChange(groupId, memberCountChange);
    }

    @Override
    public void acceptInvitation(Long gid, Long currentUid) {
        GroupMember groupMember = groupMemberService.getByGidAndUid(gid, currentUid);
        MemberCountChange memberCountChange = groupMember.changeStatusByLeader(GroupMemberStatus.APPROVED, ActionActor.MEMBER);
        groupMemberRepository.save(groupMember);

        processMemberCountChange(gid, memberCountChange);
    }


    private void processMemberCountChange(Long gid, MemberCountChange memberCountChange) {
        switch (memberCountChange) {
            case MemberCountChange.INCREMENT -> increaseMemberCount(gid);
            case MemberCountChange.DECREMENT -> decreaseMemberCount(gid);
            case MemberCountChange.NONE -> {}
        }
    }

    @Override
    @Transactional
    public void invite(Long gid, Email invitedUserEmail, Long currentUserId) {
        if (!groupMemberService.isGroupLeader(gid, currentUserId)) throw new BusinessException(ErrorCode.NOT_GROUP_LEADER);


        User invitedUser = userService.getByEmail(invitedUserEmail);


        if (groupMemberService.existsGroupMember(gid, invitedUser.getId()))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_ALREADY_EXISTS);


        groupMemberService.createInviteMember(gid, invitedUser.getId());

        applicationEventPublisher.publishEvent(
                GroupMemberInvitedEvent.builder()
                        .invitedUserId(invitedUser.getId())
                        .inviterId(currentUserId)
                        .gid(gid)
                        .build());
    }

    @Override
    @Transactional
    public void joinRequest(Long groupId, Long currentUserId) {
        if(groupMemberService.existsGroupMember(groupId, currentUserId))
            throw new BusinessException(ErrorCode.GROUP_MEMBER_ALREADY_EXISTS);

        GroupMember groupMember = GroupMember.builder()
                .gid(groupId)
                .uid(currentUserId)
                .role(GroupRole.MEMBER)
                .trackRole(GroupTrackRole.FOLLOWER)
                .status(GroupMemberStatus.REQUESTED)
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.save(groupMember);

        //알림 발생
        List<GroupMember> leaderGroupMembers = groupMemberRepository.findByGidAndRole(groupId, GroupRole.LEADER);

        if (leaderGroupMembers.isEmpty()) {
            log.error("해당 그룹에 리더가 존재하지 않습니다..? groupId : {}", groupId);
        }
        applicationEventPublisher.publishEvent(
                GroupMemberInviteRequestedEvent.builder()
                        .groupId(groupId)
                        .leaderIds(leaderGroupMembers.stream().map(GroupMember::getUid).collect(Collectors.toList()))
                        .requestedUserId(currentUserId)
                        .build()
        );
    }

    @Override
    @Transactional
    public void registerHost(Long gid, Long currentUid, Email targetUserEmail) {
        changeTrackRole(gid, currentUid, targetUserEmail, GroupTrackRole.HOST);
    }

    @Override
    @Transactional
    public void registerFollower(Long gid, Long currentUid, Email targetUserEmail) {
        changeTrackRole(gid, currentUid, targetUserEmail, GroupTrackRole.FOLLOWER);
    }

    @Override
    public GroupResponse getGroupResponseByGid(Long gid, @Nullable Long currentUid) {
        Group group = getByGid(gid);

        if (group.isPrivate()) { //PRIVATE인 그룹인 경우
            //그룹간에 관계 조회 (없다면 권한이 없다는 예외 발생)
            GroupMember groupMember = groupMemberService.findByGidAndUid(gid, currentUid)
                    .orElseThrow(() -> new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED));

            if (!(groupMember.isApproved()  //Group 속해 있거나
                    || GroupMemberStatus.INVITED.equals(groupMember.getStatus()))) //Group에 초대 받은 경우만 허용
                throw new BusinessException(ErrorCode.HANDLE_ACCESS_DENIED);
        }

        return GroupMapper.toGroupResponse(
                group,
                userService.getProfile(group.getCreatedBy()),
                fileService.findResponseByOwner(FileOwner.ofGroup(gid))
        );
    }

    @Override
    public List<GroupProfileResponse> getGroupResponseByUid(Long uid) {
        //승인된 그룹 정보 가져오기
        List<GroupMember> approvedGroupMemberList = groupMemberService.findByUidAndStatus(uid, GroupMemberStatus.APPROVED);
        List<Group> groups = groupRepository
                .findByGids(approvedGroupMemberList.stream().map(GroupMember::getGid).toList());

        Map<Long, List<FileAccessUrl>> fileaccessurlsMap
                = fileService.findAccessUrlByOwners(FileOwners.ofGroup(groups.stream().map(Group::getGid).toList()));

        Map<Long, UserProfileResponse> userProfileMap
                = userService.getProfiles(groups.stream().map(Group::getCreatedBy).collect(Collectors.toSet()));

        return groups.stream().map(group ->
                GroupMapper.toGroupProfileResponse(
                        group,
                        userProfileMap.get(group.getCreatedBy()),
                        fileaccessurlsMap.getOrDefault(group.getGid(), Collections.emptyList())))
                .toList();
    }

    @Override
    public Map<Long, GroupProfileResponse> getGroupResponseByGids(List<Long> gids) {
        return this.getGroupResponseByGids(new HashSet<>(gids));
    }

    @Override
    public Map<Long, GroupProfileResponse> getGroupResponseByGids(Set<Long> gids) {
        List<Group> groups = groupRepository.findByGids(gids.stream().toList());
        if(groups.size() != gids.size())
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);

        Map<Long, List<FileAccessUrl>> fileaccessurlsMap
                = fileService.findAccessUrlByOwners(FileOwners.ofGroup(groups.stream().map(Group::getGid).toList()));
        Map<Long, UserProfileResponse> userProfileMap
                = userService.getProfiles(groups.stream().map(Group::getCreatedBy).collect(Collectors.toSet()));

        return groups.stream().collect(
                Collectors.toMap(
                        Group::getGid,
                        group -> GroupMapper.toGroupProfileResponse(
                                group,
                                userProfileMap.get(group.getCreatedBy()),
                                fileaccessurlsMap.getOrDefault(group.getGid(), Collections.emptyList())
                        )
                )
        );
    }

    @Override
    public Page<GroupProfileResponse> searchPublicGroups(GroupSearchRequest searchRequest, Pageable pageable) {
        Page<Group> groups = this.searchPublicByName(searchRequest.getGroupName(), pageable);

        Map<Long, List<FileAccessUrl>> fileAccessUrlMap
                = fileService.findAccessUrlByOwners(FileOwners.ofGroup(groups.stream().map(Group::getGid).toList()));
        Map<Long, UserProfileResponse> userProfileMap
                = userService.getProfiles(groups.stream().map(Group::getCreatedBy).collect(Collectors.toSet()));


        return groups.map(group ->
                GroupMapper.toGroupProfileResponse(
                        group,
                        userProfileMap.get(group.getCreatedBy()),
                        fileAccessUrlMap.getOrDefault(group.getGid(), List.of())
                )
        );
    }

    @Override
    public GroupResponse create(GroupCreationRequest groupCreationRequest, Long currentUid) {
        Group group = GroupMapper.toGroup(groupCreationRequest, currentUid);

        Group savedGroup = groupRepository.save(group);
        FileOwner fileOwner = FileOwner.ofGroup(savedGroup.getGid());

        groupMemberService.createLeaderMember(savedGroup.getGid(), currentUid);

        fileService.assignAsProfile(fileOwner, groupCreationRequest.getProfileFileIds());

        return GroupMapper.toGroupResponse(savedGroup,
                userService.getProfile(group.getCreatedBy()),
                fileService.findResponseByOwner(fileOwner));
    }

    @Override
    public List<GroupJoinRequestUserProfileResponse> findMemberByStatus(Long gid, GroupMemberStatus status, Long currentUid) {
        //해당 기능은 Leader만 사용 가능
        if(!groupMemberService.isGroupLeader(gid, currentUid))
            throw new BusinessException(ErrorCode.NOT_GROUP_LEADER);

        Map<Long, GroupMember> groupMemberMap = groupMemberService.findByGidAndStatus(gid, status)
                .stream().collect(Collectors.toMap(GroupMember::getUid, Function.identity()));

        return userService.getUserCompositeByIds(groupMemberMap.keySet())
                .stream().map(userComposite -> UserMapper.toGroupJoinRequestUserProfileResponse(
                        userComposite,
                        groupMemberMap.get(userComposite.getUid())
                )).toList();
    }

    @Override
    public List<GroupMemberProfileResponse> findGroupMember(Long gid, Long currentUid) {
        //해당 기능 Member만 사용 가능
        if(!groupMemberService.isGroupMember(gid, currentUid))
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);

        Map<Long, GroupMember> groupMemberMap = groupMemberService.findByGidAndStatus(gid, GroupMemberStatus.APPROVED)
                .stream().collect(Collectors.toMap(GroupMember::getUid, Function.identity()));

        return userService.getUserCompositeByIds(groupMemberMap.keySet())
                .stream().map(userComposite -> UserMapper.toGroupMemberProfileResponse(
                        userComposite,
                        groupMemberMap.get(userComposite.getUid())
                )).toList();
    }

    @Override
    public MyGroupInfoResponse getMyGroupInfoResponse(Long gid, Long currentUid) {
        GroupMember groupMember = groupMemberService.getApprovedByGidAndUid(gid, currentUid);
        return GroupMapper.toGroupInfoResponse(groupMember);
    }

    @Override
    public GroupResponse update(Long gid, GroupUpdateRequest updateRequest, Long currentUid) {
        Group originalGroup = this.getByGid(gid);
        if(!groupMemberService.isGroupLeader(gid, currentUid))
            throw new BusinessException(ErrorCode.NOT_GROUP_LEADER);

        Group group = GroupMapper.toGroup(
                originalGroup,
                updateRequest,
                currentUid
        );

        Group updatedGroup = groupRepository.save(group);
        FileOwner fileOwner = FileOwner.ofGroup(gid);
        fileService.updateFromOwner(fileOwner, FilePurpose.PROFILE, updateRequest.getProfileFileIds());

        GroupMember leader = groupMemberService.findByGidAndRoleIsLeader(gid).getFirst();

        return GroupMapper.toGroupResponse(
                updatedGroup,
                userService.getProfile(leader.getId()),
                fileService.findResponseByOwner(fileOwner)
        );
    }

    @Override
    public void increaseMemberCount(Long gid) {
        groupRepository.increaseMemberCount(gid);
    }

    @Override
    public int decreaseMemberCount(Long gid) {
        int updated =  groupRepository.decreaseMemberCount(gid);
        if(updated == 0)
            throw new BusinessException(ErrorCode.MEMBER_COUNT_UNDERFLOW);

        return updated;
    }


    private void changeTrackRole(Long gid, Long currentUid, Email targetUserEmail, GroupTrackRole trackRole) {
        GroupMember currentGroupMember = groupMemberRepository.findByGidAndUid(gid, currentUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        //리더만 Track Role를 지정할 수 있다
        if (!currentGroupMember.isLeader()) throw new BusinessException(ErrorCode.NOT_GROUP_LEADER);

        User targetUser = userService.getByEmail(targetUserEmail);

        GroupMember registerGroupMember = groupMemberRepository.findByGidAndUid(gid, targetUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));

        if (registerGroupMember.getTrackRole() == trackRole) return;

        registerGroupMember.setTrackRole(trackRole);
        groupMemberRepository.save(registerGroupMember);

        applicationEventPublisher.publishEvent(
                GroupTrackRoleChangedEvent.builder()
                        .groupId(gid)
                        .changedRole(trackRole)
                        .roleChangedUserId(registerGroupMember.getUid())
                        .build()
        );
    }
}

package br.com.salonhub.repository;

import br.com.salonhub.entity.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {

    @Query("select photo.userId from UserPhoto photo where photo.userId in :userIds")
    Set<Long> findUserIdsWithPhoto(@Param("userIds") Collection<Long> userIds);
}

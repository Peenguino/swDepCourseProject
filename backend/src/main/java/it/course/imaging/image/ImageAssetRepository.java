package it.course.imaging.image;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, UUID> {

    List<ImageAsset> findAllByOrderByCreatedAtDesc();
}

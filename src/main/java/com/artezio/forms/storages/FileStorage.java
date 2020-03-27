package com.artezio.forms.storages;

import com.artezio.forms.storages.exceptions.EntityNotFoundException;

public interface FileStorage {

    /**
     * Save {@link FileStorageEntity} in a storage
     *
     * @param fileStorageEntity Entity to be stored
     */
    void store(FileStorageEntity fileStorageEntity);

    /**
     * Get {@link FileStorageEntity} from a storage by id
     *
     * @param id Entity's id
     * @throws EntityNotFoundException if the entity doesn't exist in the file storage
     * @return Found entity instance
     */
    FileStorageEntity retrieve(String id);

    /**
     *
     * @return Url prefix to download a file
     */
    String getDownloadUrlPrefix();

}

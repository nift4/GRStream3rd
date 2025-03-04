package net.hearnsoft.gensokyoradio.trd.model;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import kotlinx.coroutines.sync.Mutex;

/**
 * @author Yuyuko1024
 */
public class SongDataModel extends ViewModel {
    private MutableLiveData<String> title;
    public MutableLiveData<String> getTitle() {
        if (title == null) {
            title = new MutableLiveData<>();
            title.postValue("Title");
        }
        return title;
    }
    private MutableLiveData<String> artist;
    public MutableLiveData<String> getArtist() {
        if (artist == null) {
            artist = new MutableLiveData<>();
            artist.postValue("Artist");
        }
        return artist;
    }
    private MutableLiveData<String> album;
    public MutableLiveData<String> getAlbum() {
        if (album == null) {
            album = new MutableLiveData<>();
            album.postValue("Album");
        }
        return album;
    }
    private MutableLiveData<String> coverUrl;
    public MutableLiveData<String> getCoverUrl() {
        if (coverUrl == null) {
            coverUrl = new MutableLiveData<>();
            coverUrl.postValue("https://gensokyoradio.net/images/assets/gr-logo-placeholder.png");
        }
        return coverUrl;
    }
    private MutableLiveData<Boolean> isUpdatedInfo;
    public MutableLiveData<Boolean> getIsUpdatedInfo() {
        if (isUpdatedInfo == null) {
            isUpdatedInfo = new MutableLiveData<>();
            isUpdatedInfo.postValue(false);
        }
        return isUpdatedInfo;
    }
    private MutableLiveData<Integer> bufferingState;
    public MutableLiveData<Integer> getBufferingState() {
        if (bufferingState == null) {
            bufferingState = new MutableLiveData<>();
            bufferingState.postValue(0);
        }
        return bufferingState;
    }

}

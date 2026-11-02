package com.CareerNexus_Backend.CareerNexus.regression;

import com.CareerNexus_Backend.CareerNexus.dto.*;
import com.CareerNexus_Backend.CareerNexus.exceptions.UnauthorizedActionException;
import com.CareerNexus_Backend.CareerNexus.model.*;
import com.CareerNexus_Backend.CareerNexus.repository.*;
import com.CareerNexus_Backend.CareerNexus.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowRegressionTest {
    @Mock JobPostRepository jobs;
    @Mock UserAuthRepository users;
    @Mock RecruiterRepository recruiters;
    @Mock SupabaseStorageService storage;
    @InjectMocks JobPostService jobService;
    @InjectMocks RecruiterService recruiterService;

    private JobPost ownedJob() {
        JobPost job = new JobPost();
        job.setPostedBy(new User("owner", "owner@example.test", "unused", "recruiter"));
        return job;
    }
    @Test void anotherRecruiterCannotEditAJob() {
        when(jobs.findById(1L)).thenReturn(Optional.of(ownedJob()));
        assertThrows(UnauthorizedActionException.class, () -> jobService.updateJobPost(1L, new JobPostDTO(), "other"));
        verify(jobs, never()).save(any());
    }
    @Test void anotherRecruiterCannotDeleteAJob() {
        when(jobs.findById(1L)).thenReturn(Optional.of(ownedJob()));
        assertThrows(UnauthorizedActionException.class, () -> jobService.deleteJobPost(1L, "other"));
        verify(jobs, never()).delete(any());
    }
    @Test void studentCannotCreateAJob() {
        when(users.findById("student")).thenReturn(Optional.of(new User("student", "s@example.test", "unused", "student")));
        assertThrows(UnauthorizedActionException.class, () -> jobService.createJobPost(new JobPostDTO(), "student"));
        verify(jobs, never()).save(any());
    }
    @Test void recruiterCanSetUpProfileWithoutAPhoto() throws Exception {
        when(users.findById("owner")).thenReturn(Optional.of(new User("owner", "o@example.test", "unused", "recruiter")));
        when(recruiters.findById("owner")).thenReturn(Optional.empty());
        when(recruiters.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RecruiterDetailsDTO details = new RecruiterDetailsDTO(); details.setFirstName("Ava");
        assertEquals("Ava", recruiterService.createOrUpdateProfile("owner", details, null).getFirstName());
        verifyNoInteractions(storage);
    }
    @Test void editingWithoutAPhotoPreservesTheExistingImage() throws Exception {
        User user = new User("owner", "o@example.test", "unused", "recruiter");
        Recruiter profile = new Recruiter(user, "o@example.test", "Ava", "Lee", "QA", "HR", "9000000000", "https://example.test/photo.png");
        when(users.findById("owner")).thenReturn(Optional.of(user));
        when(recruiters.findById("owner")).thenReturn(Optional.of(profile));
        when(recruiters.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("https://example.test/photo.png", recruiterService.createOrUpdateProfile("owner", new RecruiterDetailsDTO(), null).getImg_loc());
        verifyNoInteractions(storage);
    }
}

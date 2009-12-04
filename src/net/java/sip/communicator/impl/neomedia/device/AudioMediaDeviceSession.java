/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.neomedia.device;


import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;

import net.java.sip.communicator.impl.neomedia.audiolevel.*;
import net.java.sip.communicator.service.neomedia.event.*;
import net.java.sip.communicator.util.*;

/**
 * Extends <tt>MediaDeviceSession</tt> to add audio-specific functionality.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 */
public class AudioMediaDeviceSession
    extends MediaDeviceSession
{
    /**
     * Our class logger.
     */
    private Logger logger = Logger.getLogger(AudioMediaDeviceSession.class);

    /**
     * The effect that we will register with our datasource in order to measure
     * audio levels of the local user audio.
     */
    private AudioLevelEffect localUserAudioLevelEffect = new AudioLevelEffect();

    /**
     * The effect that we will register with our stream in order to measure
     * audio levels of the remote user audio.
     */
    private AudioLevelEffect streamAudioLevelEffect = new AudioLevelEffect();

    /**
     * Initializes a new <tt>MediaDeviceSession</tt> instance which is to
     * represent the use of a specific <tt>MediaDevice</tt> by a
     * <tt>MediaStream</tt>.
     *
     * @param device the <tt>MediaDevice</tt> the use of which by a
     * <tt>MediaStream</tt> is to be represented by the new instance
     */
    protected AudioMediaDeviceSession(AbstractMediaDevice device)
    {
        super(device);
    }

    /**
     * Sets the  <tt>SimpleAudioLevelListener</tt> that this session should be
     * notifying about changes in local audio level related information. This
     * class only supports a single listener for audio changes per source
     * (i.e. stream or data source). Audio changes are generally quite time
     * intensive (~ 50 per second) so we are doing this in order to reduce the
     * number of objects associated with the process (such as event instances
     * listener list iterators and sync copies).
     *
     * @param listener the <tt>SimpleAudioLevelListener</tt> to add
     */
    public void setLocalUserAudioLevelListener(
                                            SimpleAudioLevelListener listener)
    {
        this.localUserAudioLevelEffect.setAudioLevelListener(listener);
    }

    /**
     * Creates an audio level effect and add its to the codec chain of the
     * <tt>TrackControl</tt> assuming that it only contains a single track.
     *
     * @param tc the track control that we need to register a level effect with.
     * @throws UnsupportedPlugInException if we <tt>tc</tt> does not support
     * effects.
     */
    private void registerLocalUserAudioLevelJMFEffect(TrackControl tc)
            throws UnsupportedPlugInException
    {
        //we register the effect regardless of whether or not we have any
        //listeners at this point because we won't get a second chance.
        //however the effect would do next to nothing unless we register a
        //first listener with it.
        //
        //XXX: i am assuming that a single effect could be reused multiple times
        // if that turns out not to be the case we need to create a new instance
        // here.
        tc.setCodecChain(new Codec[]{localUserAudioLevelEffect});
    }

    /**
     * Sets <tt>listener</tt> as the <tt>SimpleAudioLevelListener</tt> that we
     * are going to notify every time a change occurs in the audio level of
     * the media that this device session is receiving from the remote party.
     * This class only supports a single listener for audio changes per source
     * (i.e. stream or data source). Audio changes are generally quite time
     * intensive (~ 50 per second) so we are doing this in order to reduce the
     * number of objects associated with the process (such as event instances
     * listener list iterators and sync copies).
     *
     * @param listener the <tt>SimpleAudioLevelListener</tt> that we want
     * notified for audio level changes in the remote participant's media.
     */
    public void setStreamAudioLevelListener(SimpleAudioLevelListener listener)
    {
        this.streamAudioLevelEffect.setAudioLevelListener(listener);
    }

    /**
     * Adds an audio level effect to the tracks of the specified
     * <tt>trackControl</tt> and so that we would notify interested listeners
     * of audio level changes.
     *
     * @param trackControl the <tt>TrackControl</tt> where we need to register
     * a level effect that would measure the audio levels of the
     * <tt>ReceiveStream</tt> associated with this class.
     *
     * @throws UnsupportedPlugInException if we fail to add our sound level
     * effect to the track control of <tt>mediaStream</tt>'s processor.
     */
    private void registerStreamAudioLevelJMFEffect(TrackControl trackControl)
        throws UnsupportedPlugInException
    {
        //we register the effect regardless of whether or not we have any
        //listeners at this point because we won't get a second chance.
        //however the effect would do next to nothing unless we register a
        //first listener with it.
        // Assume there is only one audio track
        trackControl.setCodecChain(new Codec[]{streamAudioLevelEffect});
    }

    /**
     * Called by {@link MediaDeviceSession#addReceiveStream(ReceiveStream,
     * DataSource)} when the player associated with this session's
     * <tt>ReceiveStream</tt> moves enters the <tt>Configured</tt> state, so
     * we use the occasion to add our audio level effect.
     *
     * @param player the <tt>Processor</tt> which is the source of a
     * <tt>ConfigureCompleteEvent</tt>
     */
    @Override
    protected void configureCompleted(Processor player)
    {
        try
        {
            TrackControl tcs[] = player.getTrackControls();
            if (tcs != null)
            {
                for (TrackControl tc : tcs)
                {
                    if (tc.getFormat() instanceof AudioFormat)
                    {
                        // Assume there is only one audio track
                        registerStreamAudioLevelJMFEffect(tc);
                        break;
                    }
                }
            }
        }
        catch (UnsupportedPlugInException ex)
        {
            logger.error("The processor does not support effects", ex);
        }
    }

    /**
     * Gets notified about <tt>ControllerEvent</tt>s generated by the
     * processor reading our capture data source, calls the corresponding
     * method from the parent class so that it would initialize the processor
     * and then adds the level effect for the local user audio levels.
     *
     * @param event the <tt>ControllerEvent</tt> specifying the
     * <tt>Controller</tt> which is the source of the event and the very type of
     * the event
     */
    protected void processorControllerUpdate(ControllerEvent event)
    {
        super.processorControllerUpdate(event);

        if (event instanceof ConfigureCompleteEvent)
        {
            Processor processor = (Processor) event.getSourceController();

            if (processor != null)
            {
                // here we add sound level indicator for captured media
                // from the microphone if there are interested listeners
                try
                {
                    TrackControl tcs[] = processor.getTrackControls();

                    if (tcs != null)
                    {
                        for (TrackControl tc : tcs)
                        {
                            if (tc.getFormat() instanceof AudioFormat)
                            {
                                //we assume a single track
                                registerLocalUserAudioLevelJMFEffect(tc);
                                break;
                            }
                        }
                    }
                }
                catch (UnsupportedPlugInException ex)
                {
                    logger.error(
                        "Effects are not supported by the datasource.", ex);
                }
            }
        }
    }
}

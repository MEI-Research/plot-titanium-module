//
//  ComPlotprojectsTiPlotDelegate.m
//  plot-ios-module
//
//

#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>
#import <PlotProjects/Plot.h>
#import "ComPlotprojectsTiPlotDelegate.h"
#import "ComPlotprojectsTiNotificationFilter.h"
#import "ComPlotprojectsTiGeotriggerHandler.h"
#import "ComPlotprojectsTiConversions.h"

// define a quick NSString search function shortcut
#define stringContains(str1, str2) ([str1 rangeOfString: str2 ].location != NSNotFound)


@implementation ComPlotprojectsTiPlotDelegate
@synthesize handleNotificationDelegate;
@synthesize enableGeotriggerHandler;
@synthesize enableNotificationFilter;

-(instancetype)init {
    if ((self = [super init])) {
        filterIndex = 1;
        handlerIndex = 1;

        notificationsToFilter = [[NSMutableArray alloc] init];
        geotriggersToHandle = [[NSMutableArray alloc] init];

        notificationsBeingFiltered = [[NSMutableDictionary alloc] init];
        geotriggersBeingHandled = [[NSMutableDictionary alloc] init];

        notificationsToHandleQueued = [[NSMutableArray alloc] init];
        notificationsToFilterQueued = [[NSMutableArray alloc] init];
        geotriggersToHandleQueued = [[NSMutableArray alloc] init];

        NSLog(@"PlotProjects - Init ComPlotprojectsTiPlotDelegate");
    }
    return self;
}

-(void)initCalled {
    plotInitCalled = YES;

    NSLog(@"PlotProjects - number of notifications to filter: %ld", notificationsToFilterQueued.count);
    for (PlotFilterNotifications* n in notificationsToFilterQueued) {
        [self plotFilterNotificationsAfterInit:n];
    }
    [notificationsToFilterQueued removeAllObjects];

    NSLog(@"PlotProjects - number of notifications to handle %ld", notificationsToHandleQueued.count);
    for (UNNotificationRequest* n in notificationsToHandleQueued) {
        [handleNotificationDelegate handleNotification:n];
    }
    [notificationsToHandleQueued removeAllObjects];

    NSLog(@"PlotProjects - number of geotriggers to handle %ld", geotriggersToHandleQueued.count);
    for (PlotHandleGeotriggers* g in geotriggersToHandleQueued) {
        [self plotHandleGeotriggersAfterInit:g];
    }
    [geotriggersToHandleQueued removeAllObjects];
}

-(void)plotHandleNotification:(UNNotificationRequest*)notification data:(NSString*)data {
    //NSLog(@"PlotProjects - plotHandleNotification");
    if (plotInitCalled) {
        //NSLog(@"PlotProjects - Handling notification... %@", notification.content.userInfo);
        [handleNotificationDelegate handleNotification:notification];
    } else {
        [notificationsToHandleQueued addObject:notification];
    }
}

-(void)showNotificationsOnMainThread:(NSString *)filterId notifications:(NSArray *)notificationsPassed {
    //NSLog(@"PlotProjects - showNotificationsOnMainThread");
    PlotFilterNotifications* filterNotifications = [notificationsBeingFiltered objectForKey:filterId];
    if (filterNotifications == nil) {
        //NSLog(@"PlotProjects - Unknown filter with id: %@", filterId);
        return;
    }

    [notificationsBeingFiltered removeObjectForKey:filterId];

    NSDictionary<NSString*, UNNotificationRequest*>* index = [self indexLocalNotifications:filterNotifications.uiNotifications];

    NSMutableArray<UNNotificationRequest*>* result = [NSMutableArray array];

    for (NSDictionary* notificationData in notificationsPassed) {
        UNNotificationRequest* localNotification = [ComPlotprojectsTiConversions transformNotification:notificationData index:index];
        if (localNotification != nil) {
            [result addObject:localNotification];
        }
    }

    [filterNotifications showNotifications:result];
}

-(void)handleGeotriggersOnMainThread:(NSString*)handlerId geotriggers:(NSArray*)geotriggersPassed {
    NSLog(@"PlotProjects - handleGeotriggersOnMainThread");
    PlotHandleGeotriggers* geotriggerHandler = [geotriggersBeingHandled objectForKey:handlerId];
    if (geotriggerHandler == nil) {
        //NSLog(@"PlotProjects - Unknown handler with id: %@", handlerId);
        return;
    }

    [geotriggersBeingHandled removeObjectForKey:handlerId];

    NSDictionary* index = [self indexGeotriggers:geotriggerHandler.geotriggers];

    NSMutableArray<PlotGeotrigger*>* result = [NSMutableArray array];

    for (NSDictionary* geotriggerData in geotriggersPassed) {
        PlotGeotrigger* geotrigger = [ComPlotprojectsTiConversions transformGeotrigger:geotriggerData index:index];
        if (geotrigger != nil) {
            [result addObject:geotrigger];
        }
    }

    [geotriggerHandler markGeotriggersHandled:result];
}

-(NSDictionary<NSString*, PlotGeotrigger*>*)indexGeotriggers:(NSArray<PlotGeotrigger*>*)geotriggers {
    NSMutableDictionary<NSString*, PlotGeotrigger*>* result = [NSMutableDictionary dictionaryWithCapacity:geotriggers.count];

    for (PlotGeotrigger* geotrigger in geotriggers) {
        NSString* identifier = [geotrigger.userInfo objectForKey:@"identifier"];
        if (identifier != nil) {
            [result setObject:geotrigger forKey:identifier];
        }
    }

    return result;
}

-(NSDictionary<NSString*, UNNotificationRequest*>*)indexLocalNotifications:(NSArray<UNNotificationRequest*>*)notifications {
    NSMutableDictionary* result = [NSMutableDictionary dictionaryWithCapacity:notifications.count];

    for (UNNotificationRequest* notification in notifications) {
        if (![notification isKindOfClass:[UNNotificationRequest class]]) {
            //NSLog(@"PlotProjects - Wrong type, expected UNNotificationRequest, got %@", NSStringFromClass([notification class]));
            continue;
        }

        NSString* identifier = [notification.content.userInfo objectForKey:@"identifier"];
        if (identifier != nil) {
            [result setObject:notification forKey:identifier];
        }
    }

    return result;
}

-(void)plotFilterNotifications:(PlotFilterNotifications*)notification {
    if (plotInitCalled) {
        //NSLog(@"PlotProjects - Delegate defined, filtering notification...");
        [self plotFilterNotificationsAfterInit:notification];
    } else {
        //NSLog(@"PlotProjects - Delegate undefined, not filtering");
        [notificationsToFilterQueued addObject:notification];
    }
}

-(void)plotFilterNotificationsAfterInit:(PlotFilterNotifications *)filterNotifications {
    if (enableNotificationFilter) {
        //NSLog(@"PlotProjects - Size of list to filter id %ld", notificationsToFilter.count);

        [notificationsToFilter addObject:filterNotifications];

        ComPlotprojectsTiNotificationFilter* filter = [[ComPlotprojectsTiNotificationFilter alloc] init];
        [filter startFilter];
        [self performSelector:@selector(shutdownFilter:) withObject:filter afterDelay:10];
    } else {
        //NSLog(@"PlotProjects - showNotifications");
        [filterNotifications showNotifications:filterNotifications.uiNotifications];
    }
}

-(void)plotHandleGeotriggers:(PlotHandleGeotriggers*)geotriggers {
    NSLog(@"PlotProjects - plotHandleGeotriggers %lu", geotriggers.geotriggers.count);

    NSString* trigger_direction = @"Unknown";

    for (PlotGeotrigger* geotrigger in geotriggers.geotriggers) {
        NSLog(@"PlotProjects - for geotrigger in geotriggers loop %@", geotrigger);
        NSString* trigger = [geotrigger.userInfo objectForKey:PlotGeotriggerTrigger];

        if (trigger != nil) {
             //[result setObject:geotrigger forKey:identifier];
             NSLog(@"PlotProjects - trigger %@", trigger);

            if([trigger isEqualToString:PlotGeotriggerTriggerEnter]){
                NSLog(@"PlotProjects - ENTERED geotrigger");
                trigger_direction = @"enter";
            }
            if([trigger isEqualToString:PlotGeotriggerTriggerExit]){
                NSLog(@"PlotProjects - EXIT geotrigger");
                trigger_direction = @"exit";
            }

            if([self emaFilterRegionAllowed:trigger_direction geotrigger:geotrigger]){
                [self sendEMANotification:trigger_direction geotrigger:geotrigger];
            }
        }
    }

    if (plotInitCalled) {
        [self plotHandleGeotriggersAfterInit:geotriggers];
    } else {
        [geotriggersToHandleQueued addObject:geotriggers];
    }
}

// filter out the custom list for HealthKick and let everything else pass.
// returns true to allow region.
// returns false to block region.
-(BOOL)emaFilterRegionAllowed:(NSString*)trigger_direction geotrigger:(PlotGeotrigger*)geotrigger {
    NSString* geotrigger_name = [[geotrigger.userInfo objectForKey:PlotGeotriggerName] lowercaseString];

    if ([geotrigger_name rangeOfString:@"generic,"].location != NSNotFound) {
        return true;
    }

    // disallow the TestGrid from causing issues until we fully integrate with it
    if ([geotrigger_name rangeOfString:@"TestGrid"].location != NSNotFound) {
        return false;
    }

    NSUserDefaults *standardUserDefaults = [NSUserDefaults standardUserDefaults];

    if (standardUserDefaults) {
        NSString* custom_healthkick = [standardUserDefaults stringForKey:@"plotProjects.project"];
        // if this is the custom HealthKick app, we need to filter regions based on a whitelist.
        // otherwise, allow everything.
        if([custom_healthkick isEqualToString:@"custom_healthkick"]){

            return [self customHealthKickWhitelist:geotrigger_name];
        }
    }

    return true;
}

// Objective c doesn't support a switch statement on strings apparently.
// so we get this kinda ugly code.
-(BOOL)customHealthKickWhitelist:(NSString*)geotrigger_name{

    if ([geotrigger_name rangeOfString:@"tacobell" options:NSCaseInsensitiveSearch].location != NSNotFound){
        return true;
    } else if ([geotrigger_name rangeOfString:@"wendys" options:NSCaseInsensitiveSearch].location != NSNotFound){
        return true;
    }  else if ([geotrigger_name rangeOfString:@"subway" options:NSCaseInsensitiveSearch].location != NSNotFound){
        return true;
    }  else if ([geotrigger_name rangeOfString:@"mcdonalds" options:NSCaseInsensitiveSearch].location != NSNotFound){
        return true;
    }  else if ([geotrigger_name rangeOfString:@"burgerking" options:NSCaseInsensitiveSearch].location != NSNotFound){
        return true;
    }  else if ([geotrigger_name rangeOfString:@"kfc" options:NSCaseInsensitiveSearch].location != NSNotFound){
        return true;
    }

    // if ([geotrigger_name isEqualToString:@"[tacobell]"]) {
    //     return true;
    // } else if ([geotrigger_name isEqualToString:@"[wendys]"]) {
    //     return true;
    // } else if ([geotrigger_name isEqualToString:@"[subway]"]) {
    //     return true;
    // } else if ([geotrigger_name isEqualToString:@"[mcdonalds]"]) {
    //     return true;
    // } else if ([geotrigger_name isEqualToString:@"[burgerking]"]) {
    //     return true;
    // } else if ([geotrigger_name isEqualToString:@"[kfc]"]) {
    //     return true;
    // }

    return false;
}

-(void)sendEMANotification:(NSString*)trigger_direction geotrigger:(PlotGeotrigger*)geotrigger {
    NSUserDefaults *standardUserDefaults = [NSUserDefaults standardUserDefaults];
    NSString *notText;


    if (standardUserDefaults) {
        NSString* EMA_NOTIFICATION_IDENTIFIER = @"plotproject.ema.notify";
        // NSArray* PENDING_NOTIFICATION_IDS = [NSArray arrayWithObjects:EMA_NOTIFICATION_IDENTIFIER];
        NSString* persistentProperty = [NSString stringWithFormat:@"plot.notificationTitle.%@", trigger_direction];
        NSString* customNotTitlePersistent = [standardUserDefaults stringForKey:persistentProperty];

        if(customNotTitlePersistent != nil && ![@"" isEqualToString:customNotTitlePersistent]){
            UNUserNotificationCenter* center = [UNUserNotificationCenter currentNotificationCenter];

            // for dwell, remove any pending notifications before sending this one.
            // [center removePendingNotificationRequestsWithIdentifiers:PENDING_NOTIFICATION_IDS];
            notText = [standardUserDefaults stringForKey:@"plot.notificationText"];
            NSString* dwell_time_prop = [standardUserDefaults stringForKey:@"plot.projects_dwell_minutes"];
            NSInteger dwell_time_minutes = [dwell_time_prop integerValue];
            NSTimeInterval notTimeDelay = (dwell_time_minutes * 60.0);

            //notification code to notify location change
            UNMutableNotificationContent* content = [[UNMutableNotificationContent alloc] init];
            content.title = [NSString localizedUserNotificationStringForKey:customNotTitlePersistent arguments:nil];
            content.body = [NSString localizedUserNotificationStringForKey:notText arguments:nil];
            content.sound = [UNNotificationSound defaultSound];

            UNNotificationRequest* request = nil;

            // if DWELL is set to > 0 minutes. schedule in the future. otherwise, schedule immediately
            if (dwell_time_minutes == nil || dwell_time_minutes == 0) {
                // no dwell
                request = [UNNotificationRequest
                    requestWithIdentifier:EMA_NOTIFICATION_IDENTIFIER content:content trigger:nil];
            } else {
                // dwell set fo x minutes
                // Configure the trigger after n*60 seconds
                UNTimeIntervalNotificationTrigger* trigger = [UNTimeIntervalNotificationTrigger
                             triggerWithTimeInterval:notTimeDelay repeats: NO];

                // Create the request object.
                request = [UNNotificationRequest
                    requestWithIdentifier:EMA_NOTIFICATION_IDENTIFIER content:content trigger:trigger];
            }


            [center addNotificationRequest:request withCompletionHandler:^(NSError * _Nullable error) {
                if (error != nil) {
                   NSLog(@"PlotProjects - %@", error.localizedDescription);
                }

                NSLog(@"PlotProjects - notification scheduled");
            }];

            // detection_timestamp
            // geotrigger_id
            // geotrigger_name
            // geotrigger_direction
            NSString* trigger_id = [geotrigger.userInfo objectForKey:PlotGeotriggerIdentifier];
            NSString* trigger_timestamp = [NSString stringWithFormat:@"%lu", (long)NSDate.date.timeIntervalSince1970];
            NSString* trigger_name = stringContains([geotrigger.userInfo objectForKey:PlotGeotriggerName], @"generic,") ? @"generic" : [geotrigger.userInfo objectForKey:PlotGeotriggerName];
            NSNumber* latitude = [geotrigger.userInfo objectForKey:PlotGeotriggerGeofenceLatitude];
            NSNumber* longitude = [geotrigger.userInfo objectForKey:PlotGeotriggerGeofenceLongitude];

            //[standardUserDefaults setObject:trigger_timestamp forKey:@"plot.surveyTriggered"];

            NSDictionary *jsonDictionary = [NSDictionary dictionaryWithObjectsAndKeys:
                                            trigger_id, @"geotrigger_id",
                                            trigger_timestamp, @"detection_timestamp",
                                            trigger_name, @"geotrigger_name",
                                            trigger_direction, @"geotrigger_direction",
                                            latitude, @"latitude",
                                            longitude, @"longitude",
                                            nil];


            NSMutableArray * arr = [[NSMutableArray alloc] init];

            [arr addObject:jsonDictionary];
            NSData *jsonData2 = [NSJSONSerialization dataWithJSONObject:arr options:NSJSONWritingPrettyPrinted error:nil];
            NSString *jsonString = [[NSString alloc] initWithData:jsonData2 encoding:NSUTF8StringEncoding];
            NSLog(@"jsonData as string:\n%@", jsonString);

            [standardUserDefaults setObject:jsonString forKey:@"plot.surveyTriggered"];

            [standardUserDefaults synchronize];

            NSLog(@"PlotProjects - .fired event ComPlotprojectsTiModule");
        }
    }
}

-(void)plotHandleGeotriggersAfterInit:(PlotHandleGeotriggers *)geotriggers {
    if (enableGeotriggerHandler) {
        NSLog(@"PlotProjects - plotHandleGeotriggersAfterInit filter");
        [geotriggersToHandle addObject:geotriggers];
        ComPlotprojectsTiGeotriggerHandler* handler = [[ComPlotprojectsTiGeotriggerHandler alloc] init];
        [handler startHandler];
        [self performSelector:@selector(shutdownHandler:) withObject:handler afterDelay:10];
    } else {
        NSLog(@"PlotProjects - markGeotriggersHandled:");
        [geotriggers markGeotriggersHandled:geotriggers.geotriggers];
    }
}

-(void)shutdownFilter:(ComPlotprojectsTiNotificationFilter*)filter {
    [filter shutdown];
}

-(void)shutdownHandler:(ComPlotprojectsTiGeotriggerHandler*)handler {
    [handler shutdown];
}

-(void)popFilterableNotificationsOnMainThread:(NSMutableDictionary*)result {
    NSArray* notifications;
    NSString* filterId = @"";

    if (notificationsToFilter.count == 0u) {
        //NSLog(@"PlotProjects - pop1 number of notifications to filter %ld", notificationsToFilter.count);
        notifications = @[];
    } else {
        PlotFilterNotifications* n = [notificationsToFilter objectAtIndex:0];
        [notificationsToFilter removeObjectAtIndex:0];

        notifications = n.uiNotifications;
        //NSLog(@"PlotProjects - pop2 number of notifications to filter %ld", notificationsToFilter.count);

        filterId = [NSString stringWithFormat:@"%d", filterIndex++];
        [notificationsBeingFiltered setObject:n forKey:filterId];
    }

    NSMutableArray* jsonNotifications = [NSMutableArray array];
    for (UNNotificationRequest* localNotification in notifications) {
        [jsonNotifications addObject:[ComPlotprojectsTiConversions localNotificationToDictionary:localNotification]];
    }

    [result setObject:filterId forKey:@"filterId"];
    [result setObject:jsonNotifications forKey:@"notifications"];
}

-(void)popGeotriggersOnMainThread:(NSMutableDictionary*)result {
    NSLog(@"PlotProjects - popGeotriggersOnMainThread");
    NSArray* geotriggers;
    NSString* handlerId = @"";
    if (geotriggersToHandle.count == 0u) {
        geotriggers = @[];
    } else {
        PlotHandleGeotriggers* n = [geotriggersToHandle objectAtIndex:0];
        [geotriggersToHandle removeObjectAtIndex:0];

        geotriggers = n.geotriggers;
        handlerId = [NSString stringWithFormat:@"%d", handlerIndex++];
        if (geotriggersBeingHandled == nil) {
            geotriggersBeingHandled = [NSMutableDictionary dictionary];
        }
        [geotriggersBeingHandled setObject:n forKey:handlerId];
    }

    NSMutableArray* jsonGeotriggers = [NSMutableArray array];
    for (PlotGeotrigger* geotrigger in geotriggers) {
        [jsonGeotriggers addObject:[ComPlotprojectsTiConversions geotriggerToDictionary:geotrigger]];
    }

    [result setObject:handlerId forKey:@"handlerId"];
    [result setObject:jsonGeotriggers forKey:@"geotriggers"];
}

@end
